package org.allaymc.bedrocktunnel.rules;

import org.allaymc.bedrocktunnel.capture.CapturedPacket;

import java.util.List;

public record RuleSet(PacketControlMode controlMode, List<PacketRule> blockRules, List<PacketRule> breakpointRules) {
    public static final RuleSet DEFAULT = new RuleSet(PacketControlMode.BLACKLIST, List.of(), List.of());

    public RuleSet {
        blockRules = List.copyOf(blockRules);
        breakpointRules = List.copyOf(breakpointRules);
    }

    public boolean isBlocked(CapturedPacket packet) {
        boolean matched = blockRules.stream().anyMatch(rule -> rule.matches(packet));
        return controlMode == PacketControlMode.BLACKLIST ? matched : !matched;
    }

    public boolean hitsBreakpoint(CapturedPacket packet) {
        return breakpointRules.stream().anyMatch(rule -> rule.matches(packet));
    }
}
