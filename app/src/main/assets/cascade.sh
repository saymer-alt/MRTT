#!/bin/bash
# Каскад между VPS на протоколе MRTT: клиент на этом сервере подключается к серверу-выходу напрямую,
# как обычный конфиг. Какие клиенты идут через него, решает сервер MRTT правилами ip rule на таблицу.
set -e
action="$1"; id="$2"
case "$id" in ''|*[!a-f0-9]*) echo "error: bad id"; exit 2 ;; esac
dir=/etc/mrtt-exit
iface="mrtx$id"
chain="MRTTX$id"
unit="mrtt-exit-$id"
binary=/usr/local/lib/mrtt-exit-client

table_of() { sed -n 's/^MRTT_EXIT_TABLE=//p' "$dir/$id.env" 2>/dev/null; }

case "$action" in
client-up)
    host="$3"; port="$4"; password="$5"
    [ -x "$binary" ] || { echo "error: клиент каскада не загрузился"; exit 3; }
    mkdir -p "$dir"
    used="$(cat "$dir"/*.env 2>/dev/null | sed -n 's/^MRTT_EXIT_TABLE=//p')"
    table=2001
    while echo "$used" | grep -qx "$table"; do table=$((table + 1)); done
    umask 077
    cat > "$dir/$id.env" <<EOF
MRTT_PASSWORD=$password
MRTT_DEVICE_ID=cascade-$id
MRTT_DEVICE_NAME=cascade
MRTT_EXIT_TABLE=$table
EOF
    # До подключения: пока туннеля нет, клиенты с этим выходом теряют связь, а не уходят через этот сервер.
    cat > "$dir/$id-pre.sh" <<EOF
#!/bin/sh
sysctl -qw net.ipv4.ip_forward=1
ip route replace blackhole default metric 1000 table $table
iptables -t nat -N $chain 2>/dev/null || true
iptables -t nat -C POSTROUTING -o $iface -j $chain 2>/dev/null || iptables -t nat -I POSTROUTING 1 -o $iface -j $chain
iptables -C FORWARD -o $iface -j ACCEPT 2>/dev/null || iptables -I FORWARD -o $iface -j ACCEPT
iptables -C FORWARD -i $iface -j ACCEPT 2>/dev/null || iptables -I FORWARD -i $iface -j ACCEPT
iptables -t mangle -C FORWARD -o $iface -p tcp --tcp-flags SYN,RST SYN -j TCPMSS --clamp-mss-to-pmtu 2>/dev/null || \
    iptables -t mangle -A FORWARD -o $iface -p tcp --tcp-flags SYN,RST SYN -j TCPMSS --clamp-mss-to-pmtu
EOF
    # Адрес на интерфейс не вешаем: у клиента этого сервера может быть такой же, подсети совпадают.
    cat > "$dir/$id-up.sh" <<EOF
#!/bin/sh
ip link set $iface up mtu 1280
sysctl -qw net.ipv4.conf.$iface.rp_filter=2
ip route replace default dev $iface table $table
iptables -t nat -F $chain
iptables -t nat -A $chain -j SNAT --to-source "\$1"
EOF
    chmod 700 "$dir/$id-pre.sh" "$dir/$id-up.sh"
    cat > "/etc/systemd/system/$unit.service" <<EOF
[Unit]
Description=MRTT cascade exit $id
After=network-online.target
Wants=network-online.target

[Service]
EnvironmentFile=$dir/$id.env
ExecStartPre=$dir/$id-pre.sh
ExecStart=$binary --jitsi direct.invalid/exit --peer $host:$port -n 8 --exact-workers --srtp-mode dtls --listen 127.0.0.1:0 --tun-uds mrtt_exit_$id --exit-tun $iface --exit-hook $dir/$id-up.sh
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
    systemctl daemon-reload
    systemctl enable --now "$unit" >/dev/null 2>&1 || { echo "error: клиент каскада не запустился"; exit 5; }
    echo "CASCADE_CLIENT $table"
    ;;
client-check)
    # Интерфейс появляется, когда второй сервер принял пароль и выдал адрес; таблица ведёт в него после настройки.
    table="$(table_of)"
    for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
        if ip route show table "$table" 2>/dev/null | grep -q "dev $iface"; then echo "CASCADE_OK"; exit 0; fi
        sleep 2
    done
    echo "error: второй сервер не ответил: $(journalctl -u "$unit" -n 1 --no-pager -o cat 2>/dev/null | cut -c1-160)"
    exit 6
    ;;
client-down)
    table="$(table_of)"
    systemctl disable --now "$unit" >/dev/null 2>&1 || true
    iptables -t nat -D POSTROUTING -o "$iface" -j "$chain" 2>/dev/null || true
    iptables -t nat -F "$chain" 2>/dev/null || true
    iptables -t nat -X "$chain" 2>/dev/null || true
    iptables -D FORWARD -o "$iface" -j ACCEPT 2>/dev/null || true
    iptables -D FORWARD -i "$iface" -j ACCEPT 2>/dev/null || true
    iptables -t mangle -D FORWARD -o "$iface" -p tcp --tcp-flags SYN,RST SYN -j TCPMSS --clamp-mss-to-pmtu 2>/dev/null || true
    [ -z "$table" ] || ip route flush table "$table" 2>/dev/null || true
    rm -f "/etc/systemd/system/$unit.service" "$dir/$id.env" "$dir/$id-pre.sh" "$dir/$id-up.sh"
    systemctl daemon-reload
    echo "CASCADE_OK"
    ;;
*) echo "error: unknown action"; exit 2 ;;
esac
