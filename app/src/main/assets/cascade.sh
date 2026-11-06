#!/bin/bash
# Каскад между VPS: WireGuard от сервера с клиентами (client) к серверу-выходу (gw).
# Какие клиенты идут в туннель, решает сервер MRTT правилами ip rule на таблицу из client-up.
set -e
action="$1"; id="$2"
case "$id" in ''|*[!a-f0-9]*) echo "error: bad id"; exit 2 ;; esac

ensure_wg() {
    command -v wg-quick >/dev/null 2>&1 && return 0
    if command -v apt-get >/dev/null 2>&1; then
        DEBIAN_FRONTEND=noninteractive apt-get update -qq >/dev/null
        DEBIAN_FRONTEND=noninteractive apt-get install -y -qq --no-install-recommends wireguard-tools >/dev/null
    elif command -v dnf >/dev/null 2>&1; then dnf install -y -q wireguard-tools >/dev/null
    elif command -v yum >/dev/null 2>&1; then yum install -y -q wireguard-tools >/dev/null
    elif command -v pacman >/dev/null 2>&1; then pacman -Sy --noconfirm wireguard-tools >/dev/null
    elif command -v apk >/dev/null 2>&1; then apk add -q wireguard-tools >/dev/null
    else echo "error: не удалось установить wireguard-tools"; exit 3
    fi
    command -v wg-quick >/dev/null 2>&1 || { echo "error: wireguard-tools не установился"; exit 3; }
    mkdir -p /etc/wireguard
}

stop_link() {
    systemctl disable --now "wg-quick@$1" >/dev/null 2>&1 || true
    rm -f "/etc/wireguard/$1.conf"
}

case "$action" in
gw-init)
    ensure_wg
    iface="mrtg$id"; conf="/etc/wireguard/$iface.conf"
    used="$(cat /etc/wireguard/mrtg*.conf 2>/dev/null | sed -n 's/^Address = 10\.77\.\([0-9]*\)\..*/\1/p')"
    n=1
    while echo "$used" | grep -qx "$n"; do n=$((n + 1)); done
    [ "$n" -le 250 ] || { echo "error: нет свободных адресов"; exit 4; }
    port=$((51820 + n))
    wan="$(ip -4 route get 1.1.1.1 | sed -n 's/.* dev \([^ ]*\).*/\1/p' | head -n1)"
    [ -n "$wan" ] || { echo "error: нет маршрута в интернет"; exit 4; }
    key="$(wg genkey)"
    umask 077
    cat > "$conf" <<EOF
[Interface]
Address = 10.77.$n.1/30
ListenPort = $port
PrivateKey = $key
MTU = 1380
PostUp = sysctl -qw net.ipv4.ip_forward=1
PostUp = iptables -I INPUT -p udp --dport $port -j ACCEPT
PostUp = iptables -I FORWARD -i %i -j ACCEPT
PostUp = iptables -I FORWARD -o %i -j ACCEPT
PostUp = iptables -t nat -A POSTROUTING -s 10.77.$n.0/30 -o $wan -j MASQUERADE
PostDown = iptables -D INPUT -p udp --dport $port -j ACCEPT
PostDown = iptables -D FORWARD -i %i -j ACCEPT
PostDown = iptables -D FORWARD -o %i -j ACCEPT
PostDown = iptables -t nat -D POSTROUTING -s 10.77.$n.0/30 -o $wan -j MASQUERADE
EOF
    if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q "Status: active"; then
        ufw allow "$port/udp" >/dev/null 2>&1 || true
    fi
    echo "CASCADE_GW $(echo "$key" | wg pubkey) $port $n"
    ;;
gw-peer)
    iface="mrtg$id"; conf="/etc/wireguard/$iface.conf"
    n="$(sed -n 's/^Address = 10\.77\.\([0-9]*\)\..*/\1/p' "$conf")"
    printf '\n[Peer]\nPublicKey = %s\nAllowedIPs = 10.77.%s.2/32\n' "$3" "$n" >> "$conf"
    systemctl enable --now "wg-quick@$iface" >/dev/null 2>&1 || { echo "error: WireGuard не запустился на выходе"; exit 5; }
    echo "CASCADE_OK"
    ;;
client-up)
    ensure_wg
    iface="mrtc$id"; conf="/etc/wireguard/$iface.conf"
    host="$3"; port="$4"; n="$5"; peer="$6"
    used="$(cat /etc/wireguard/mrtc*.conf 2>/dev/null | sed -n 's/^Table = //p')"
    table=2001
    while echo "$used" | grep -qx "$table"; do table=$((table + 1)); done
    key="$(wg genkey)"
    umask 077
    cat > "$conf" <<EOF
[Interface]
Address = 10.77.$n.2/32
PrivateKey = $key
MTU = 1380
Table = $table
PostUp = sysctl -qw net.ipv4.ip_forward=1 net.ipv4.conf.%i.rp_filter=2
PostUp = ip route add blackhole default metric 1000 table $table
PostUp = iptables -I FORWARD -i %i -j ACCEPT
PostUp = iptables -I FORWARD -o %i -j ACCEPT
PostUp = iptables -t nat -A POSTROUTING -o %i -j MASQUERADE
PostUp = iptables -t mangle -A FORWARD -o %i -p tcp --tcp-flags SYN,RST SYN -j TCPMSS --clamp-mss-to-pmtu
PostDown = ip route del blackhole default metric 1000 table $table
PostDown = iptables -D FORWARD -i %i -j ACCEPT
PostDown = iptables -D FORWARD -o %i -j ACCEPT
PostDown = iptables -t nat -D POSTROUTING -o %i -j MASQUERADE
PostDown = iptables -t mangle -D FORWARD -o %i -p tcp --tcp-flags SYN,RST SYN -j TCPMSS --clamp-mss-to-pmtu

[Peer]
PublicKey = $peer
Endpoint = $host:$port
AllowedIPs = 0.0.0.0/0
PersistentKeepalive = 25
EOF
    systemctl enable --now "wg-quick@$iface" >/dev/null 2>&1 || { echo "error: WireGuard не запустился"; exit 5; }
    echo "CASCADE_CLIENT $(echo "$key" | wg pubkey) $table"
    ;;
client-check)
    # Рукопожатие доказывает, что UDP до выхода проходит и ключи совпали.
    iface="mrtc$id"
    for _ in 1 2 3 4 5 6 7 8 9 10; do
        stamp="$(wg show "$iface" latest-handshakes 2>/dev/null | awk '{print $2}')"
        if [ -n "$stamp" ] && [ "$stamp" != "0" ]; then echo "CASCADE_OK"; exit 0; fi
        sleep 2
    done
    echo "error: нет рукопожатия с выходом"
    exit 6
    ;;
client-down) stop_link "mrtc$id"; echo "CASCADE_OK" ;;
gw-down) stop_link "mrtg$id"; echo "CASCADE_OK" ;;
*) echo "error: unknown action"; exit 2 ;;
esac
