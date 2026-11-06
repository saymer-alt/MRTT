//! Which clients leave through which exit server: `ip rule from <client> lookup <table>`. The tables belong to the
//! cascade clients the app sets up; these rules are the only part the server keeps in line with its settings.

use std::collections::BTreeSet;

/// Between the proxy's fwmark rules and the main table, and unused by anything else.
pub const RULE_PREF: &str = "5300";

/// Our rules out of `ip -4 rule show`, as (client ip, table).
pub fn parse_rules(text: &str) -> BTreeSet<(String, u32)> {
    text.lines()
        .filter_map(|line| {
            let (pref, rest) = line.split_once(':')?;
            if pref.trim() != RULE_PREF {
                return None;
            }
            let mut words = rest.split_whitespace();
            if words.next()? != "from" {
                return None;
            }
            let ip = words.next()?.to_owned();
            if words.next()? != "lookup" {
                return None;
            }
            Some((ip, words.next()?.parse().ok()?))
        })
        .collect()
}

/// `ip rule` arguments that turn `current` into `desired`: removals first, so that a client changing exits has one rule.
pub fn plan(current: &BTreeSet<(String, u32)>, desired: &BTreeSet<(String, u32)>) -> Vec<Vec<String>> {
    let command = |verb: &str, (ip, table): &(String, u32)| {
        ["rule", verb, "from", ip, "lookup", &table.to_string(), "pref", RULE_PREF].map(str::to_owned).to_vec()
    };
    let removed = current.difference(desired).map(|rule| command("del", rule));
    let added = desired.difference(current).map(|rule| command("add", rule));
    removed.chain(added).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rules(list: &[(&str, u32)]) -> BTreeSet<(String, u32)> {
        list.iter().map(|(ip, table)| ((*ip).to_owned(), *table)).collect()
    }

    #[test]
    fn only_our_rules_are_read() {
        let text = "0:\tfrom all lookup local\n5209:\tfrom all fwmark 0x1 lookup 100\n\
                    5300:\tfrom 10.66.67.3 lookup 2001\n5300:\tfrom 10.66.67.4 lookup 2002\n32766:\tfrom all lookup main\n";
        assert_eq!(parse_rules(text), rules(&[("10.66.67.3", 2001), ("10.66.67.4", 2002)]));
    }

    #[test]
    fn plan_touches_only_the_difference() {
        let current = rules(&[("10.66.67.3", 2001), ("10.66.67.4", 2001)]);
        let desired = rules(&[("10.66.67.3", 2001), ("10.66.67.4", 2002), ("10.66.67.5", 2001)]);
        let steps: Vec<String> = plan(&current, &desired).iter().map(|step| step.join(" ")).collect();
        assert_eq!(
            steps,
            [
                "rule del from 10.66.67.4 lookup 2001 pref 5300",
                "rule add from 10.66.67.4 lookup 2002 pref 5300",
                "rule add from 10.66.67.5 lookup 2001 pref 5300",
            ]
        );
        assert!(plan(&desired, &desired).is_empty());
    }
}
