package jp.skillarena;

import org.bukkit.entity.Player;
import java.util.*;

/** Raid encounter registry, independent of ordinary four-player parties.
 * Register participants when a raid boss starts, and clear on encounter end.
 * All registered players share a friendly-fire protection group.
 */
public final class RaidManager {
  private final Map<UUID, Set<UUID>> raids = new HashMap<>();
  private final Map<UUID, UUID> memberships = new HashMap<>();

  public void startRaid(UUID encounterId, Collection<UUID> participants) {
    endRaid(encounterId);
    Set<UUID> members = new HashSet<>();
    for(UUID id : participants) {
      UUID old = memberships.get(id);
      if(old != null) endRaid(old);
      members.add(id);
    }
    raids.put(encounterId, members);
    for(UUID id : members) memberships.put(id, encounterId);
  }

  public boolean sameRaid(Player a, Player b) {
    if(a == null || b == null || a.equals(b) || a.getWorld() != b.getWorld()) return false;
    UUID id = memberships.get(a.getUniqueId());
    return id != null && id.equals(memberships.get(b.getUniqueId())) && raids.containsKey(id);
  }

  public void endRaid(UUID encounterId) {
    Set<UUID> members = raids.remove(encounterId);
    if(members != null) for(UUID id : members) memberships.remove(id, encounterId);
  }

  public void clear() { raids.clear(); memberships.clear(); }
}
