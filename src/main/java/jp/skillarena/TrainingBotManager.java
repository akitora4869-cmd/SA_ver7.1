package jp.skillarena;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import java.util.*;

/** PvE training companions: server-side simulated combatants, not fake Player connections. */
public final class TrainingBotManager implements Listener {
  private final JavaPlugin plugin;
  private final Map<UUID,Bot> bots=new LinkedHashMap<>();
  private final Random random=new Random();
  private final BukkitTask task;
  private static final String[] SKILLS={"火炎弾","氷槍","雷撃","光弾","斬撃","衝撃拳","空間刃","生命吸収"};
  private static final class Bot {
    final Zombie mob; final UUID owner; final int[] load={0,4,5,7};
    double hp=100,mp=100; int slot=0; long nextCast=0,nextDodge=0,nextDash=0,nextHeal=0; int potions=3;
    Bot(Zombie mob,UUID owner){this.mob=mob;this.owner=owner;}
  }
  public TrainingBotManager(JavaPlugin plugin){this.plugin=plugin;Bukkit.getPluginManager().registerEvents(this,plugin);task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,10L,5L);}
  public boolean isBot(Entity entity){return entity!=null&&bots.containsKey(entity.getUniqueId());}
  public void shutdown(){task.cancel();for(Bot b:new ArrayList<>(bots.values()))b.mob.remove();bots.clear();}
  public List<String> status(Player owner){List<String> out=new ArrayList<>();for(Bot b:bots.values())if(b.owner.equals(owner.getUniqueId()))out.add("§b"+b.mob.getCustomName()+" §cHP "+(int)b.hp+" §bMP "+(int)b.mp+" §d回復薬 "+b.potions);return out;}
  public boolean command(CommandSender sender,String[] args){
    if(!(sender instanceof Player player)){sender.sendMessage("§cゲーム内で実行してください。");return true;}
    if(!player.hasPermission("skillarena.admin")){player.sendMessage("§c管理者権限が必要です。");return true;}
    String action=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
    switch(action){
      case "spawn":{
        long count=bots.values().stream().filter(b->b.owner.equals(player.getUniqueId())).count();
        if(count>=3){player.sendMessage("§cテストBotは1人につき最大3体です。");return true;}
        Location at=player.getLocation().clone().add(2+count,0,1);
        Zombie mob=at.getWorld().spawn(at,Zombie.class,z->{z.setAdult();z.setAI(true);z.setRemoveWhenFarAway(false);z.setCanPickupItems(false);z.setCustomNameVisible(true);z.setCustomName("§b訓練Bot-"+(count+1));z.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(100);z.setHealth(100);z.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(.34);z.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));z.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));});
        bots.put(mob.getUniqueId(),new Bot(mob,player.getUniqueId()));player.sendMessage("§a訓練Botを召喚しました。/testbot skills で構成を確認できます。");return true;
      }
      case "clear":{int n=0;for(Bot b:new ArrayList<>(bots.values()))if(b.owner.equals(player.getUniqueId())){b.mob.remove();bots.remove(b.mob.getUniqueId());n++;}player.sendMessage("§eBotを"+n+"体削除しました。");return true;}
      case "list":{List<String> lines=status(player);if(lines.isEmpty())player.sendMessage("§7Botがいません。");else lines.forEach(player::sendMessage);return true;}
      case "skills":{for(Bot b:bots.values())if(b.owner.equals(player.getUniqueId())){StringJoiner j=new StringJoiner(" / ");for(int id:b.load)j.add(SKILLS[id]);player.sendMessage(b.mob.getCustomName()+" §f"+j);}return true;}
      case "set":{
        if(args.length<3){player.sendMessage("§e/testbot set <スロット1-4> <スキルID0-7>");return true;}
        try{int slot=Integer.parseInt(args[1])-1,id=Integer.parseInt(args[2]);if(slot<0||slot>3||id<0||id>=SKILLS.length)throw new NumberFormatException();
          int changed=0;for(Bot b:bots.values())if(b.owner.equals(player.getUniqueId())){b.load[slot]=id;changed++;}
          player.sendMessage("§a"+changed+"体のスロット"+(slot+1)+"を"+SKILLS[id]+"に設定しました。");
        }catch(NumberFormatException ex){player.sendMessage("§cスロット1-4、スキルID0-7で指定してください。");}return true;
      }
      default:player.sendMessage("§e/testbot spawn|clear|list|skills|set <1-4> <0-7>");return true;
    }
  }
  public List<String> complete(String[] args){if(args.length==1)return Arrays.asList("spawn","clear","list","skills","set");if(args.length==2&&args[0].equalsIgnoreCase("set"))return Arrays.asList("1","2","3","4");if(args.length==3&&args[0].equalsIgnoreCase("set"))return Arrays.asList("0","1","2","3","4","5","6","7");return List.of();}
  private void tick(){long now=System.currentTimeMillis();for(Bot b:new ArrayList<>(bots.values())){
    Zombie mob=b.mob;Player owner=Bukkit.getPlayer(b.owner);
    if(!mob.isValid()||mob.isDead()||owner==null||!owner.isOnline()){mob.remove();bots.remove(mob.getUniqueId());continue;}
    b.mp=Math.min(100,b.mp+1.5);
    if(b.hp<=40&&b.potions>0&&now>=b.nextHeal){b.hp=Math.min(100,b.hp+40);b.potions--;b.nextHeal=now+12000;mob.getWorld().spawnParticle(Particle.HEART,mob.getEyeLocation(),12,.4,.5,.4,.1);mob.getWorld().playSound(mob.getLocation(),Sound.ENTITY_GENERIC_DRINK,.8f,1.2f);}
    mob.setCustomName("§b訓練Bot §c"+(int)Math.ceil(b.hp)+"HP §b"+(int)b.mp+"MP");
    LivingEntity enemy=findEnemy(b,owner);
    if(enemy==null){if(mob.getLocation().distanceSquared(owner.getLocation())>18*18&&mob.getWorld()==owner.getWorld())mob.teleport(owner.getLocation().clone().add(1,0,1));else mob.setTarget(null);continue;}
    if(enemy instanceof Player)mob.setTarget((Player)enemy);else mob.setTarget(null);
    Vector direction=enemy.getLocation().toVector().subtract(mob.getLocation().toVector()).setY(0);
    double dist=direction.length();if(dist<.01)continue;direction.normalize();
    if(now>=b.nextDodge&&dist<8&&random.nextDouble()<.10){Vector side=new Vector(-direction.getZ(),0,direction.getX()).multiply(random.nextBoolean()?.95:-.95);side.setY(.36);mob.setVelocity(side);b.nextDodge=now+3200;mob.getWorld().spawnParticle(Particle.CLOUD,mob.getLocation(),14,.3,.3,.3,.04);}
    else if(now>=b.nextDash&&dist>5&&dist<20&&random.nextDouble()<.14){Vector dash=direction.clone().multiply(1.12).setY(.28);mob.setVelocity(dash);b.nextDash=now+4500;mob.getWorld().spawnParticle(Particle.CLOUD,mob.getLocation(),18,.4,.2,.4,.04);}
    else if(dist>3){Vector velocity=mob.getVelocity();mob.setVelocity(direction.clone().multiply(dist>8?.37:.22).setY(velocity.getY()));}
    if(now<b.nextCast)continue;
    int skill=b.load[b.slot++%4];double cost=(skill==4||skill==5)?15:12;
    if(b.mp<cost)continue;
    boolean melee=skill==4||skill==5||skill==7;
    if(dist>(melee?3.5:15))continue;
    b.mp-=cost;b.nextCast=now+1600+random.nextInt(1100);
    double damage=(skill==4?24:skill==5?20:skill==7?11:12);
    Location fx=enemy.getLocation().clone().add(0,1,0);
    Particle particle=switch(skill){case 0->Particle.FLAME;case 1->Particle.SNOWFLAKE;case 2->Particle.ELECTRIC_SPARK;case 3->Particle.END_ROD;case 4,5->Particle.CRIT;case 6->Particle.PORTAL;default->Particle.SOUL;};
    mob.getWorld().spawnParticle(particle,fx,32,.6,.8,.6,.06);
    mob.getWorld().playSound(mob.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,.8f,1.15f);
    // Combat test bots use simulated skill mechanics, not a Player's actual SkillArena skill executor.
    if(enemy instanceof Player victim){victim.damage(damage,mob);}else if(plugin instanceof SkillArenaPlugin arena && arena.damageRaidBossFromBot(enemy,damage,b.owner)){ /* boss virtual HP */ }else enemy.damage(damage,mob);
    if(skill==5){Vector push=direction.clone().multiply(.8).setY(.35);enemy.setVelocity(push);}
    if(skill==7){b.hp=Math.min(100,b.hp+damage*.45);mob.getWorld().spawnParticle(Particle.HEART,mob.getLocation().add(0,1,0),5,.3,.3,.3,.02);}
  }}
  // Bot enemies: hostile monsters (including raid boss), never other players or companion bots.
  private LivingEntity findEnemy(Bot b,Player owner){LivingEntity closest=null;double best=24*24;for(Entity entity:b.mob.getNearbyEntities(24,12,24)){
    if(!(entity instanceof Monster target)||entity.equals(b.mob)||isBot(entity)||!target.isValid()||target.isDead())continue;
    double d=entity.getLocation().distanceSquared(b.mob.getLocation());if(d<best){best=d;closest=target;}
  }return closest;}
  /** Virtual bot HP is authoritative: direct damage avoids cancelled Bukkit entity damage events. */
  public void damageInRadius(Location center,double radius,double height,double amount,boolean push,double force,double lift,Set<UUID> raidMembers){
    for(Bot b:new ArrayList<>(bots.values())){
      Player owner=Bukkit.getPlayer(b.owner);
      if(owner==null||!raidMembers.contains(b.owner)||b.mob.getWorld()!=center.getWorld())continue;
      Vector delta=b.mob.getLocation().toVector().subtract(center.toVector());
      if(Math.abs(delta.getY())>height||delta.clone().setY(0).lengthSquared()>radius*radius)continue;
      applyBossDamage(b,amount);
      if(push&&b.mob.isValid()){Vector away=delta.setY(0);if(away.lengthSquared()<.001)away=new Vector(1,0,0);b.mob.setVelocity(away.normalize().multiply(force).setY(lift));}
    }
  }
  public void damageInBossAttack(Location origin,Vector direction,String move,double amount,Entity source,Set<UUID> raidMembers){
    for(Bot b:new ArrayList<>(bots.values())){
      if(!raidMembers.contains(b.owner)||b.mob.getWorld()!=origin.getWorld()||!b.mob.isValid())continue;
      Vector delta=b.mob.getLocation().toVector().subtract(origin.toVector()).setY(0);
      double along=delta.dot(direction),lateral=delta.clone().subtract(direction.clone().multiply(along)).length();
      boolean hit=switch(move){
        case "BREAK"->along>=0&&along<=4&&lateral<1.5;
        case "BLAST"->delta.length()< (source instanceof Zombie z && z.getCustomName()!=null && !z.getCustomName().contains("ヴァルガス")?4:5);
        case "TEMPEST"->delta.length()<6;
        case "GENOCIDE"->along>=0&&along<14&&lateral<2;
        default->delta.length()<14;
      };
      if(!hit)continue;
      applyBossDamage(b,amount);
      if(b.mob.isValid()&&(move.equals("BLAST")||move.equals("TEMPEST")||move.equals("RAGNAROK"))){Vector away=delta.lengthSquared()<.001?direction.clone():delta.normalize();b.mob.setVelocity(away.multiply(move.equals("RAGNAROK")?1.55:1.05).setY(.65));}
    }
  }
  private void applyBossDamage(Bot b,double amount){
    if(!b.mob.isValid())return;
    b.hp=Math.max(0,b.hp-Math.max(0,amount));
    b.mob.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,b.mob.getLocation().add(0,1,0),8,.35,.3,.35,.05);
    if(b.hp<=0){b.mob.getWorld().spawnParticle(Particle.SMOKE_LARGE,b.mob.getLocation(),25,.5,.5,.5,.03);bots.remove(b.mob.getUniqueId());b.mob.remove();}
  }
  @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void damage(EntityDamageEvent event){Bot b=bots.get(event.getEntity().getUniqueId());if(b==null)return;event.setCancelled(true);double dmg=event.getDamage();if(event instanceof EntityDamageByEntityEvent by){Entity attacker=by.getDamager();if(attacker instanceof Player p && p.getUniqueId().equals(b.owner))return;if(attacker instanceof Player)return;}b.hp-=Math.max(0,dmg);if(b.hp<=0){b.mob.getWorld().spawnParticle(Particle.SMOKE_LARGE,b.mob.getLocation(),25,.5,.5,.5,.03);b.mob.remove();bots.remove(b.mob.getUniqueId());}}
  @EventHandler public void death(EntityDeathEvent event){if(bots.remove(event.getEntity().getUniqueId())!=null){event.getDrops().clear();event.setDroppedExp(0);}}
  @EventHandler public void combust(EntityCombustEvent event){if(isBot(event.getEntity()))event.setCancelled(true);}
}
