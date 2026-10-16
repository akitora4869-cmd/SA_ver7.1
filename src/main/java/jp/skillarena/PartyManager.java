package jp.skillarena;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Small in-memory cooperative parties and a per-viewer HP/MP sidebar. */
public final class PartyManager {
  private final SkillArenaPlugin plugin;
  private final Map<UUID, Group> memberships = new HashMap<>();
  private final Map<UUID, Invitation> invitations = new HashMap<>();
  private final Map<UUID, Scoreboard> previousBoards = new HashMap<>();
  private final BukkitTask displayTask;
  private static final int LIMIT=4;
  private static final long INVITE_MS=60000;
  private static final class Group { final UUID leader; final LinkedHashSet<UUID> members=new LinkedHashSet<>(); Group(UUID id){leader=id;members.add(id);} }
  private static final class Invitation {final UUID leader;final long expiry;Invitation(UUID l){leader=l;expiry=System.currentTimeMillis()+INVITE_MS;}}
  public PartyManager(SkillArenaPlugin plugin){this.plugin=plugin;displayTask=Bukkit.getScheduler().runTaskTimer(plugin,this::updateDisplays,10L,10L);}
  public List<UUID> members(Player p){Group g=memberships.get(p.getUniqueId());return g==null?List.of(p.getUniqueId()):new ArrayList<>(g.members);}
  public boolean sameParty(Player a,Player b){Group g=memberships.get(a.getUniqueId());return g!=null&&g==memberships.get(b.getUniqueId());}
  public boolean handle(CommandSender sender,String[] args){
    if(!(sender instanceof Player p)){sender.sendMessage("§cゲーム内から使用してください。");return true;}
    String action=args.length==0?"list":args[0].toLowerCase(Locale.ROOT);
    UUID id=p.getUniqueId();Group g=memberships.get(id);
    switch(action){
      case "create":{
        if(g!=null){p.sendMessage("§c既にパーティに参加しています。");return true;}
        Group created=new Group(id);memberships.put(id,created);p.sendMessage("§aパーティを作成しました。/party invite <名前> で招待できます。");return true;
      }
      case "invite":{
        if(g==null||!g.leader.equals(id)){p.sendMessage("§cリーダーのみ招待できます。まず /party create を使用してください。");return true;}
        if(args.length<2){p.sendMessage("§e/party invite <プレイヤー名>");return true;}
        Player target=Bukkit.getPlayerExact(args[1]);if(target==null){p.sendMessage("§c対象がオンラインではありません。");return true;}
        if(target.equals(p)||memberships.containsKey(target.getUniqueId())){p.sendMessage("§cそのプレイヤーは招待できません。");return true;}
        if(g.members.size()>=LIMIT){p.sendMessage("§cパーティは最大4人です。");return true;}
        invitations.put(target.getUniqueId(),new Invitation(id));target.sendMessage("§b"+p.getName()+"§fからパーティ招待が届きました。§a/party accept §7(60秒以内)");p.sendMessage("§a"+target.getName()+" に招待を送りました。");return true;
      }
      case "accept":{
        if(g!=null){p.sendMessage("§c先に現在のパーティを抜けてください。");return true;}
        Invitation inv=invitations.remove(id);if(inv==null||inv.expiry<System.currentTimeMillis()){p.sendMessage("§c有効な招待がありません。");return true;}
        Group dest=memberships.get(inv.leader);if(dest==null||dest.members.size()>=LIMIT){p.sendMessage("§c招待元のパーティに参加できません。");return true;}
        dest.members.add(id);memberships.put(id,dest);broadcast(dest,"§a"+p.getName()+" がパーティに参加しました。");return true;
      }
      case "leave":{
        if(g==null){p.sendMessage("§cパーティに参加していません。");return true;}
        if(g.leader.equals(id)){disband(g);p.sendMessage("§eパーティを解散しました。");}
        else {g.members.remove(id);memberships.remove(id);restore(p);broadcast(g,"§e"+p.getName()+" が脱退しました。");p.sendMessage("§eパーティを脱退しました。");}return true;
      }
      case "kick":{
        if(g==null||!g.leader.equals(id)){p.sendMessage("§cリーダーのみ除名できます。");return true;}
        if(args.length<2){p.sendMessage("§e/party kick <名前>");return true;}
        Player target=Bukkit.getPlayerExact(args[1]);if(target==null||target.equals(p)||memberships.get(target.getUniqueId())!=g){p.sendMessage("§c対象はパーティにいません。");return true;}
        g.members.remove(target.getUniqueId());memberships.remove(target.getUniqueId());restore(target);target.sendMessage("§cパーティから除名されました。");broadcast(g,"§e"+target.getName()+" が除名されました。");return true;
      }
      case "disband":{
        if(g==null||!g.leader.equals(id)){p.sendMessage("§cリーダーのみ解散できます。");return true;}
        disband(g);return true;
      }
      case "list":{
        if(g==null){p.sendMessage("§7パーティ未参加。§e/party create §7で作成できます。");return true;}
        p.sendMessage("§bパーティ §7("+g.members.size()+"/"+LIMIT+")");for(UUID member:g.members){Player online=Bukkit.getPlayer(member);p.sendMessage((member.equals(g.leader)?"§6★ ":"§7・ ")+(online==null?Bukkit.getOfflinePlayer(member).getName():online.getName()));}return true;
      }
      default:p.sendMessage("§e/party create|invite <名前>|accept|leave|kick <名前>|disband|list");return true;
    }
  }
  private void broadcast(Group g,String msg){for(UUID id:g.members){Player p=Bukkit.getPlayer(id);if(p!=null)p.sendMessage(msg);}}
  private void disband(Group g){broadcast(g,"§eパーティが解散しました。");for(UUID id:new ArrayList<>(g.members)){memberships.remove(id);Player p=Bukkit.getPlayer(id);if(p!=null)restore(p);}}
  private void updateDisplays(){
    for(Player viewer:Bukkit.getOnlinePlayers()){
      Group g=memberships.get(viewer.getUniqueId());if(g==null){if(previousBoards.containsKey(viewer.getUniqueId()))restore(viewer);continue;}
      if(!previousBoards.containsKey(viewer.getUniqueId()))previousBoards.put(viewer.getUniqueId(),viewer.getScoreboard());
      Scoreboard board=Bukkit.getScoreboardManager().getNewScoreboard();Objective obj=board.registerNewObjective("sa_party","dummy","§b§lPARTY §7HP / MP");obj.setDisplaySlot(DisplaySlot.SIDEBAR);
      int line=15;int idx=0;
      for(UUID member:g.members){
        Player p=Bukkit.getPlayer(member);String name=p==null?"OFFLINE":p.getName();if(name.length()>14)name=name.substring(0,14);
        String nameLine="§f"+name+" §"+idx;obj.getScore(nameLine).setScore(line--);
        String stats=p==null?"§8---":("§cHP "+(int)Math.ceil(p.getHealth())+" §bMP "+(int)plugin.partyMp(p));
        obj.getScore(stats+" §"+idx).setScore(line--);idx++;
      }
      viewer.setScoreboard(board);
    }
  }
  private void restore(Player p){Scoreboard old=previousBoards.remove(p.getUniqueId());if(old!=null)p.setScoreboard(old);}
  public void onQuit(Player p){invitations.remove(p.getUniqueId());restore(p);Group g=memberships.get(p.getUniqueId());if(g==null)return;if(g.leader.equals(p.getUniqueId()))disband(g);else{g.members.remove(p.getUniqueId());memberships.remove(p.getUniqueId());broadcast(g,"§e"+p.getName()+" が退出しました。");}}
  public void shutdown(){displayTask.cancel();for(Player p:Bukkit.getOnlinePlayers())restore(p);memberships.clear();invitations.clear();}
  public List<String> complete(CommandSender sender,String[] args){List<String> candidates=new ArrayList<>();if(args.length==1)candidates.addAll(Arrays.asList("create","invite","accept","leave","kick","disband","list"));else if(args.length==2&&(args[0].equalsIgnoreCase("invite")||args[0].equalsIgnoreCase("kick")))for(Player p:Bukkit.getOnlinePlayers())if(!p.getName().equals(sender.getName()))candidates.add(p.getName());String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);candidates.removeIf(s->!s.toLowerCase(Locale.ROOT).startsWith(prefix));return candidates;}
}
