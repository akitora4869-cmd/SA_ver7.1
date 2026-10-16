package jp.skillarena.boss;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import java.util.*;

/** Server-authoritative Vargas combat prototype. A packet player visual can replace the fallback entity. */
public final class VargasBoss implements Listener {
  private final JavaPlugin plugin;
  private final Random random = new Random();
  private Zombie body;
  private final Map<UUID, Echo> echoes = new HashMap<>();
  private static final class Echo {
    final Zombie zombie; double hp = 400; long nextMove = 0, nextLeap = 0;
    Echo(Zombie zombie){this.zombie=zombie;}
  }
  private PacketVargasVisual visual;
  private BossBar bar;
  private BukkitTask ticker;
  private final Set<UUID> party = new HashSet<>();
  private UUID raidId;
  private final Map<UUID,Long> recentThreat = new HashMap<>();
  private final Map<UUID,Long> dodgeUntil = new HashMap<>();
  private double maxHp, currentHp;
  private int phase, ticks;
  private long nextAction, recoveryUntil, nextEvade, nextLeap, nextStrafeChange, nextAreaAttack, nextCombatManeuver;
  private boolean skyfallActive;
  private int skyfallStartTick;
  private UUID skyfallTarget;
  private int strafeDirection=1;
  private String currentMove = "";
  private boolean pending;
  private final Map<String,Long> cooldowns = new HashMap<>();

  public VargasBoss(JavaPlugin plugin){this.plugin=plugin;Bukkit.getPluginManager().registerEvents(this,plugin);}
  public boolean active(){return body!=null&&body.isValid()&&!body.isDead();}
  /** Direct virtual HP damage from an authorized companion bot. */
  public boolean botDamage(LivingEntity target,double damage,UUID owner){
    if(!active()||!party.contains(owner))return false;
    if(target.getUniqueId().equals(body.getUniqueId())){currentHp=Math.max(0,currentHp-Math.max(0,damage));if(currentHp<=0)end(true);return true;}
    Echo echo=echoes.get(target.getUniqueId());if(echo==null)return false;
    echo.hp=Math.max(0,echo.hp-Math.max(0,damage));
    if(echo.hp<=0){echoes.remove(target.getUniqueId());echo.zombie.remove();}else echo.zombie.setCustomName("§7剣帝の分身 §f"+(int)Math.ceil(echo.hp)+"/400");
    return true;
  }
  /** Scripted boss hits must not be swallowed by vanilla zombie melee immunity ticks. */
  private void damagePlayer(Player p,double amount,LivingEntity attacker){
    if(p.isDead()||!p.isOnline())return;
    p.setNoDamageTicks(0);
    p.damage(amount,attacker);
  }
  public boolean isParticipant(Player player){return active()&&party.contains(player.getUniqueId());}
  public void start(Player leader){
    if(active()){leader.sendMessage("§c剣帝ヴァルガスは既に出現しています。");return;}
    World world=leader.getWorld();Location spawn=leader.getLocation().clone().add(leader.getLocation().getDirection().setY(0).normalize().multiply(9));
    spawn.setY(world.getHighestBlockYAt(spawn)+1);
    party.clear();
    if(plugin instanceof jp.skillarena.SkillArenaPlugin arena){
      for(UUID id:arena.partyMembers(leader)){Player member=Bukkit.getPlayer(id);if(member!=null&&member.getWorld()==world&&member.getGameMode()!=GameMode.SPECTATOR&&party.size()<4)party.add(id);}
    }
    if(party.isEmpty())party.add(leader.getUniqueId());
    raidId=UUID.randomUUID();
    if(plugin instanceof jp.skillarena.SkillArenaPlugin arena)arena.raids().startRaid(raidId,party);
    int size=party.size();double scale=new double[]{0,1,1.75,2.4,3}[size];maxHp=2000*scale;currentHp=maxHp;
    body=world.spawn(spawn,Zombie.class,z->{z.setAdult();z.setAI(true);z.setGravity(true);z.setSilent(true);z.setRemoveWhenFarAway(false);z.setCanPickupItems(false);z.setCustomName("§4剣帝ヴァルガス");z.setCustomNameVisible(true);z.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20);z.setHealth(20);z.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(.37);z.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));z.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));z.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));z.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));z.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));});
    // Use the real netherite-armored zombie model. No packet-based fake player visual.
    visual=null;
    clearEchoes();phase=1;ticks=0;nextAction=0;recoveryUntil=0;nextEvade=0;nextLeap=0;nextStrafeChange=0;nextAreaAttack=0;nextCombatManeuver=0;strafeDirection=1;pending=false;skyfallActive=false;skyfallTarget=null;cooldowns.clear();dodgeUntil.clear();recentThreat.clear();
    bar=Bukkit.createBossBar("§4剣帝ヴァルガス §7| PHASE 1 §f"+(int)maxHp+"/"+(int)maxHp,BarColor.RED,BarStyle.SEGMENTED_10);
    for(UUID id:party){Player p=Bukkit.getPlayer(id);if(p!=null){bar.addPlayer(p);p.sendTitle("§4§l剣帝ヴァルガス","§7挑戦開始",10,45,15);}}
    ticker=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
  }
  /** Phase changes are signaled visually, without chat or title warnings. */
  private void showPhaseSparks(int enteredPhase){
    for(int delay=0;delay<=28;delay+=2){
      Bukkit.getScheduler().runTaskLater(plugin,()->{
        if(!active()||phase!=enteredPhase)return;
        Location center=body.getLocation().clone().add(0,1.1,0);
        World world=center.getWorld();
        double spread=enteredPhase>=3?1.3:.95;
        world.spawnParticle(Particle.CRIT,center,28,spread,.9,spread,.32);
        world.spawnParticle(Particle.FLAME,center,enteredPhase>=3?25:14,spread,.8,spread,.055);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME,center,enteredPhase>=3?24:12,spread,.9,spread,.05);
      },delay);
    }
  }
  private List<Player> players(){List<Player> out=new ArrayList<>();if(body==null)return out;for(UUID id:party){Player p=Bukkit.getPlayer(id);if(p!=null&&p.isOnline()&&!p.isDead()&&p.getWorld()==body.getWorld()&&p.getGameMode()!=GameMode.SPECTATOR&&p.getLocation().distanceSquared(body.getLocation())<65*65)out.add(p);}return out;}
  private Player target(){List<Player> ps=players();if(ps.isEmpty())return null;ps.sort(Comparator.comparingDouble(p->p.getLocation().distanceSquared(body.getLocation())-(recentThreat.getOrDefault(p.getUniqueId(),0L)>System.currentTimeMillis()?16:0)));return ps.get(0);}
  private void tick(){if(!active()){end(false);return;}ticks++;if(players().isEmpty()){end(false);return;}
    double ratio=currentHp/maxHp;int next=ratio<=.20?3:ratio<=.50?2:1;
    if(next>phase){int previous=phase;phase=next;recoveryUntil=ticks+35;pending=false;nextAction=ticks+85;nextAreaAttack=ticks+105;body.getWorld().strikeLightningEffect(body.getLocation());body.getWorld().playSound(body.getLocation(),Sound.ENTITY_WITHER_SPAWN,1,.8f);showPhaseSparks(phase);if(previous<2)spawnEchoes();}
    bar.setProgress(Math.max(0,Math.min(1,ratio)));bar.setTitle("§4剣帝ヴァルガス §7| PHASE "+phase+" §f"+(int)Math.ceil(currentHp)+"/"+(int)maxHp);
    if(ticks%2!=0)return;
    tickEchoes();
    if(skyfallActive){tickSkyfall();return;}
    Player t=target();if(t==null)return;
    // Keep vanilla navigation enabled for terrain/pathfinding; velocity adds combat strafing.
    if(body.getTarget()!=t)body.setTarget(t);
    Location at=body.getLocation();Vector toward=t.getLocation().toVector().subtract(at.toVector());double distance=toward.length();if(distance<.1)return;
    Vector flat=toward.clone().setY(0);if(flat.lengthSquared()<.01)return;flat.normalize();
    body.setRotation((float)Math.toDegrees(Math.atan2(-flat.getX(),flat.getZ())),0);
    if(distance>=15.0&&distance<=45.0&&ticks>=nextLeap&&ticks>=nextAreaAttack&&ticks>=nextAction&&body.isOnGround()&&!pending&&ticks>=recoveryUntil){
      startSkyfall(t);return;
    }
    if(ticks<recoveryUntil||pending){
      if(pending && distance>2){Vector side=new Vector(-flat.getZ(),0,flat.getX()).multiply(strafeDirection*.13);move(body,side);}
      else body.setVelocity(new Vector(0,body.getVelocity().getY(),0));
      return;
    }
    // Combat AI: continuously orbit the target, close gaps and retreat from point-blank range.
    if(ticks>=nextStrafeChange){strafeDirection=random.nextBoolean()?1:-1;nextStrafeChange=ticks+24+random.nextInt(36);}
    Vector side=new Vector(-flat.getZ(),0,flat.getX()).multiply(strafeDirection);
    Vector stride;
    if(distance>6.5)stride=flat.clone().multiply(.38).add(side.clone().multiply(.13));
    else if(distance>3.0)stride=flat.clone().multiply(.18).add(side.clone().multiply(.27));
    else if(distance>1.7)stride=side.clone().multiply(.32);
    else stride=flat.clone().multiply(-.22).add(side.clone().multiply(.24));
    move(body,stride);
    // Leap toward a distant player, including a small vertical impulse for visible jump attacks.
    if(distance>7&&distance<12&&ticks>=nextLeap&&body.isOnGround()&&random.nextDouble()<.11){
      nextLeap=ticks+90;Vector jump=flat.clone().multiply(1.32);jump.setY(.68);
      body.setVelocity(jump);body.getWorld().spawnParticle(Particle.CLOUD,at.clone().add(0,.2,0),24,.5,.1,.5,.05);
      body.getWorld().playSound(at,Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,1,.85f);
    }
    // Dedicated boss combat beats: feint, circle or close distance without immediately attacking.
    // These are real movement windows so melee players can safely approach between area attacks.
    if(ticks>=nextCombatManeuver && !pending && ticks>=recoveryUntil){
      nextCombatManeuver=ticks+65+random.nextInt(50);
      if(random.nextDouble()<.65){combatManeuver(t,flat,distance);return;}
    }
    if(ticks<nextAction)return;
    String attack=choose(distance);
    if(attack==null){nextAction=ticks+18;return;}
    startMove(attack,t);
  }
  /** Long-distance gap closer: a visible high leap, then an impact shockwave on landing. */
  private void startSkyfall(Player target){
    skyfallActive=true;skyfallStartTick=ticks;skyfallTarget=target.getUniqueId();
    nextLeap=ticks+260;nextAreaAttack=ticks+150;nextAction=ticks+110;pending=false;
    Location origin=body.getLocation();Vector horizontal=target.getLocation().toVector().subtract(origin.toVector()).setY(0);
    double distance=horizontal.length();if(distance<.1){skyfallActive=false;return;}
    horizontal.normalize();
    // Horizontal impulse scales with the gap but is capped to avoid extreme launches.
    double speed=Math.min(1.55,Math.max(.85,distance/13.0));
    body.setVelocity(horizontal.multiply(speed).setY(.93));
    World w=origin.getWorld();w.spawnParticle(Particle.CLOUD,origin.clone().add(0,.3,0),65,.7,.3,.7,.09);
    w.spawnParticle(Particle.SOUL_FIRE_FLAME,origin.clone().add(0,1,0),28,.5,.6,.5,.04);
    w.playSound(origin,Sound.ENTITY_ENDER_DRAGON_FLAP,1.3f,.7f);
  }
  private void tickSkyfall(){
    if(!active()){skyfallActive=false;return;}
    int elapsed=ticks-skyfallStartTick;
    Location at=body.getLocation();World w=at.getWorld();
    if(ticks%4==0){w.spawnParticle(Particle.CLOUD,at.clone().add(0,.3,0),10,.35,.2,.35,.02);
      w.spawnParticle(Particle.CRIT,at.clone().add(0,.8,0),12,.4,.5,.4,.02);}
    // At the apex, make a modest midair correction toward the selected player.
    if(elapsed==10&&skyfallTarget!=null){Player t=Bukkit.getPlayer(skyfallTarget);
      if(t!=null&&t.isOnline()&&t.getWorld()==w){Vector to=t.getLocation().toVector().subtract(at.toVector()).setY(0);
        if(to.lengthSquared()>.01){to.normalize().multiply(.85);body.setVelocity(new Vector(to.getX(),body.getVelocity().getY(),to.getZ()));}}}
    // Do not trigger on the launch tick; also resolve if blocked or stuck in the air.
    if(elapsed>=8&&(body.isOnGround()||elapsed>=50)){
      skyfallActive=false;skyfallTarget=null;recoveryUntil=ticks+48;nextAction=Math.max(nextAction,ticks+75);
      skyfallImpact(at);
    }
  }
  private void skyfallImpact(Location center){
    World w=center.getWorld();double radius=phase>=3?7.5:phase==2?6.5:6.0;
    double damage=phase>=3?17:phase==2?14:11;
    w.playSound(center,Sound.ENTITY_GENERIC_EXPLODE,1.5f,.65f);
    w.spawnParticle(Particle.EXPLOSION_LARGE,center.clone().add(0,.5,0),4,.7,.2,.7,0);
    for(int ring=0;ring<3;ring++){
      final int index=ring;
      Bukkit.getScheduler().runTaskLater(plugin,()->{
        if(!active()||center.getWorld()!=body.getWorld())return;
        double r=radius*(index+1)/3.0;
        for(int i=0;i<56;i++){double a=i*Math.PI/28.0;Location pt=center.clone().add(Math.cos(a)*r,.2,Math.sin(a)*r);
          w.spawnParticle(Particle.CLOUD,pt,2,.15,.12,.15,.015);
          w.spawnParticle(Particle.BLOCK_CRACK,pt,2,.12,.1,.12,0,Material.DIRT.createBlockData());}
      },ring*3L);
    }
    if(plugin instanceof jp.skillarena.SkillArenaPlugin arena)arena.trainingBots().damageInRadius(center,radius,3.5,damage,true,1.15,.65,party);
    for(Player p:players()){
      if(Math.abs(p.getLocation().getY()-center.getY())>3.5)continue;
      Vector away=p.getLocation().toVector().subtract(center.toVector()).setY(0);
      if(away.lengthSquared()>radius*radius||System.currentTimeMillis()<dodgeUntil.getOrDefault(p.getUniqueId(),0L))continue;
      damagePlayer(p,damage,body);
      if(away.lengthSquared()<.001)away=new Vector(1,0,0);
      away.normalize().multiply(1.15).setY(.65);p.setVelocity(away);
    }
  }
  /** Move with real velocity so the zombie visibly walks instead of teleporting each step. */
  private void move(Zombie z,Vector delta){
    Location at=z.getLocation();Location ahead=at.clone().add(delta.clone().normalize().multiply(.65));
    if(!ahead.getBlock().isPassable()||!ahead.clone().add(0,1,0).getBlock().isPassable()){
      if(z.isOnGround()&&ahead.clone().add(0,1,0).getBlock().isPassable()&&ahead.clone().add(0,2,0).getBlock().isPassable()){
        z.setVelocity(new Vector(delta.getX(),.43,delta.getZ()));
      }
      return;
    }
    if(!ahead.clone().subtract(0,1,0).getBlock().isSolid()&&!ahead.clone().subtract(0,2,0).getBlock().isSolid())return;
    Vector old=z.getVelocity();z.setVelocity(new Vector(delta.getX(),old.getY(),delta.getZ()));
  }
  /** Called by the player's projectile simulation before collision. Cooldown and chance prevent perfect dodges. */
  public void warnProjectile(Player attacker,Location projectile,Vector velocity){
    if(active()&&party.contains(attacker.getUniqueId()))for(Echo e:echoes.values()){
      Zombie z=e.zombie;if(!z.isValid()||ticks<e.nextLeap||z.getWorld()!=projectile.getWorld())continue;
      Vector incoming=z.getLocation().clone().add(0,1,0).toVector().subtract(projectile.toVector());
      if(incoming.lengthSquared()>25||incoming.lengthSquared()<.01)continue;
      Vector direction=velocity.clone();if(direction.lengthSquared()<.001)continue;direction.normalize();
      double along=incoming.dot(direction);
      if(along>0&&along<5&&incoming.subtract(direction.multiply(along)).lengthSquared()<2&&random.nextDouble()<.28){
        e.nextLeap=ticks+85;Vector side=new Vector(-velocity.getZ(),0,velocity.getX());
        if(side.lengthSquared()>.01){side.normalize().multiply(random.nextBoolean()?.65:-.65).setY(.3);z.setVelocity(side);}
      }
    }
    if(!active()||pending||ticks<recoveryUntil||ticks<nextEvade||!party.contains(attacker.getUniqueId()))return;
    if(!body.getWorld().equals(projectile.getWorld()))return;
    Vector incoming=body.getLocation().clone().add(0,1,0).toVector().subtract(projectile.toVector());
    if(incoming.lengthSquared()>49||incoming.lengthSquared()<.01)return;
    Vector travel=velocity.clone().normalize();
    double forward=incoming.dot(travel);
    if(forward<0||forward>7||incoming.clone().subtract(travel.multiply(forward)).lengthSquared()>2.25)return;
    nextEvade=ticks+65;
    if(random.nextDouble()>.55)return;
    Vector side=new Vector(-velocity.getZ(),0,velocity.getX());
    if(side.lengthSquared()<.01)return;
    side.normalize().multiply(random.nextBoolean()?1:-1);
    Location at=body.getLocation();
    Location landing=at.clone().add(side.clone().multiply(2.2));
    if(!landing.getBlock().isPassable()||!landing.clone().add(0,1,0).getBlock().isPassable())side.multiply(-1);
    Vector evade=side.multiply(.95);evade.setY(body.isOnGround()?.34:body.getVelocity().getY());
    body.setVelocity(evade);
    body.getWorld().spawnParticle(Particle.CLOUD,at.clone().add(0,.8,0),22,.4,.5,.4,.04);
    body.getWorld().playSound(at,Sound.ENTITY_ENDERMAN_TELEPORT,.6f,1.7f);
  }
  /** Phase 2: a weaker armored echo uses the same pursuit, leap, evasion and sword-move vocabulary. */
  private void spawnEchoes(){
    if(!active()||!echoes.isEmpty())return;
    Location center=body.getLocation();World w=center.getWorld();
    Location spawn=center.clone().add(2.5,0,1.5);
    if(!spawn.getBlock().isPassable()||!spawn.clone().add(0,1,0).getBlock().isPassable())spawn=center.clone().add(0,0,2.5);
    Zombie z=w.spawn(spawn,Zombie.class,m->{
      m.setAdult();m.setAI(true);m.setSilent(true);m.setRemoveWhenFarAway(false);m.setCanPickupItems(false);
      m.setCustomName("§7剣帝の分身 §f400/400");m.setCustomNameVisible(true);
      m.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20);m.setHealth(20);m.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(.29);
      m.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
      m.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
      m.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
      m.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
      m.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
    });
    echoes.put(z.getUniqueId(),new Echo(z));
    w.spawnParticle(Particle.SOUL,spawn.clone().add(0,1,0),100,.8,1,.8,.08);
    w.playSound(spawn,Sound.ENTITY_ILLUSIONER_MIRROR_MOVE,1,.7f);
  }
  private void clearEchoes(){for(Echo e:echoes.values())if(e.zombie.isValid())e.zombie.remove();echoes.clear();}
  private void tickEchoes(){
    for(Echo e:new ArrayList<>(echoes.values())){
      Zombie z=e.zombie;if(!z.isValid()||z.isDead()){echoes.remove(z.getUniqueId());continue;}
      Player t=null;double best=Double.MAX_VALUE;
      for(Player p:players()){double d=p.getLocation().distanceSquared(z.getLocation());if(d<best){best=d;t=p;}}
      if(t==null)continue;
      if(z.getTarget()!=t)z.setTarget(t);
      Vector to=t.getLocation().toVector().subtract(z.getLocation().toVector()).setY(0);
      double distance=to.length();if(distance<.1)continue;Vector dir=to.normalize();
      z.setRotation((float)Math.toDegrees(Math.atan2(-dir.getX(),dir.getZ())),0);
      Vector side=new Vector(-dir.getZ(),0,dir.getX()).multiply((ticks/34)%2==0?1:-1);
      if(distance>5.5)move(z,dir.clone().multiply(.27).add(side.clone().multiply(.09)));
      else if(distance>2.5)move(z,dir.clone().multiply(.11).add(side.clone().multiply(.21)));
      else move(z,side.multiply(.24).add(dir.multiply(-.09)));
      if(distance>6&&distance<15&&ticks>=e.nextLeap&&z.isOnGround()&&random.nextDouble()<.08){
        e.nextLeap=ticks+115;z.setVelocity(dir.clone().multiply(.85).setY(.48));
        z.getWorld().spawnParticle(Particle.CLOUD,z.getLocation(),14,.35,.15,.35,.04);
      }
      if(ticks<e.nextMove)continue;
      String attack=distance<5?(random.nextBoolean()?"BREAK":"BLAST"):(distance<9?"TEMPEST":"GENOCIDE");
      e.nextMove=ticks+100+random.nextInt(65);final Player victim=t;final String selected=attack;
      Location telegraph=z.getLocation().clone();z.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,telegraph.clone().add(0,1,0),22,.7,.5,.7,.02);
      z.getWorld().playSound(telegraph,Sound.ITEM_TRIDENT_RIPTIDE_1,.5f,1.3f);
      Bukkit.getScheduler().runTaskLater(plugin,()->{
        if(!active()||!z.isValid()||z.isDead()||!echoes.containsKey(z.getUniqueId())||!victim.isOnline())return;
        Location at=z.getLocation();Vector facing=victim.getLocation().toVector().subtract(at.toVector()).setY(0);
        if(facing.lengthSquared()<.01)return;facing.normalize();
        if(selected.equals("GENOCIDE"))z.setVelocity(facing.clone().multiply(1.15).setY(.3));
        z.getWorld().spawnParticle(selected.equals("BLAST")||selected.equals("TEMPEST")?Particle.CLOUD:Particle.SWEEP_ATTACK,at.clone().add(0,1,0),35,1,.5,1,.08);
        double radius=selected.equals("TEMPEST")?5:selected.equals("BLAST")?4:3;
        if(plugin instanceof jp.skillarena.SkillArenaPlugin arena){
          arena.trainingBots().damageInBossAttack(at,facing,selected,5.0,z,party);
        }
        for(Player p:players()){
          Vector delta=p.getLocation().toVector().subtract(at.toVector()).setY(0);
          double along=delta.dot(facing);double lateral=delta.clone().subtract(facing.clone().multiply(along)).length();
          boolean hit=selected.equals("BLAST")||selected.equals("TEMPEST")?delta.length()<=radius:along>=0&&along< (selected.equals("GENOCIDE")?10:3.5)&&lateral<1.6;
          if(hit&&System.currentTimeMillis()>=dodgeUntil.getOrDefault(p.getUniqueId(),0L)){
            damagePlayer(p,5.0,z); // deliberately below the main boss's 10-24 damage
            if(selected.equals("BLAST")||selected.equals("TEMPEST")){
              Vector away=delta.lengthSquared()<.01?facing.clone():delta.normalize();
              p.setVelocity(away.multiply(.65).setY(.35));
            }
          }
        }
      },12);
    }
  }
  /** Combat-only movement action: a readable feint or reposition rather than another area attack. */
  private void combatManeuver(Player target,Vector toward,double distance){
    Location at=body.getLocation();
    Vector side=new Vector(-toward.getZ(),0,toward.getX()).multiply(random.nextBoolean()?1:-1);
    if(distance<5){ // give melee players an opening, then sidestep instead of blasting
      body.setVelocity(side.multiply(.48).add(toward.clone().multiply(-.16)).setY(body.isOnGround()?.20:body.getVelocity().getY()));
      body.getWorld().spawnParticle(Particle.CLOUD,at.clone().add(0,.3,0),14,.4,.1,.4,.025);
    }else if(distance>8){ // advancing jump; deliberately does not deal damage
      body.setVelocity(toward.clone().multiply(1.05).add(side.multiply(.10)).setY(body.isOnGround()?.55:body.getVelocity().getY()));
    }else{
      body.setVelocity(side.multiply(.46).add(toward.clone().multiply(.13)).setY(body.getVelocity().getY()));
    }
    nextAction=Math.max(nextAction,ticks+28+random.nextInt(18));
  }
  private boolean areaMove(String move){return move.equals("BLAST")||move.equals("TEMPEST")||move.equals("RAGNAROK");}
  private String choose(double distance){
    List<String> options=new ArrayList<>();
    if(distance<4.8)options.add("BREAK");
    if(distance>=3.5&&distance<15)options.add("GENOCIDE");
    // Area moves have a shared cooldown. No consecutive radial attacks.
    if(ticks>=nextAreaAttack){
      if(distance<5.5)options.add("BLAST");
      if(distance<7.5)options.add("TEMPEST");
      if(phase==3&&ticks>160&&distance<12)options.add("RAGNAROK");
    }
    Collections.shuffle(options,random);
    for(String attack:options)if(cooldowns.getOrDefault(attack,0L)<=ticks)return attack;
    return null;
  }
  private void startMove(String name,Player target){currentMove=name;pending=true;int telegraph=name.equals("RAGNAROK")?40:name.equals("GENOCIDE")?16:12;
    long ct=switch(name){case "BREAK"->38;case "BLAST"->65;case "TEMPEST"->90;case "GENOCIDE"->75;default->240;};cooldowns.put(name,(long)ticks+ct);
    // Mandatory 2.5–4 second attack-free interval, plus a longer shared area-attack lockout.
    int breathingRoom=50+random.nextInt(31);
    nextAction=ticks+telegraph+breathingRoom;
    recoveryUntil=ticks+telegraph+16;
    nextCombatManeuver=Math.min(nextCombatManeuver,ticks+telegraph+22);
    if(areaMove(name))nextAreaAttack=ticks+telegraph+135+random.nextInt(46);
    Location origin=body.getLocation().clone();Vector dir=target.getLocation().toVector().subtract(origin.toVector()).setY(0).normalize();
    double range=name.equals("GENOCIDE")?13:name.equals("RAGNAROK")?14:name.equals("TEMPEST")?6:name.equals("BLAST")?5:4;
    Particle particle=phase>=2?Particle.SOUL_FIRE_FLAME:Particle.CRIT;
    body.getWorld().playSound(origin,Sound.ITEM_TRIDENT_RIPTIDE_1,.8f,.65f);
    for(int j=0;j<telegraph;j+=3){int step=j;Bukkit.getScheduler().runTaskLater(plugin,()->{if(!active()||!pending||!currentMove.equals(name))return;Location at=body.getLocation();for(double d=1;d<range;d+=1.3){Location point=at.clone().add(dir.clone().multiply(d)).add(0,.25,0);body.getWorld().spawnParticle(particle,point,7,.3,.2,.3,.025);}},step);}
    Bukkit.getScheduler().runTaskLater(plugin,()->{if(!active()||!pending||!currentMove.equals(name))return;pending=false;execute(name,dir);},telegraph);
  }
  private void execute(String move,Vector dir){if(visual!=null)visual.swing();Location origin=body.getLocation().clone();World w=origin.getWorld();
    w.playSound(origin,Sound.ENTITY_PLAYER_ATTACK_SWEEP,1.6f,.6f);
    w.spawnParticle(Particle.CRIT,origin.clone().add(0,1,0),85,2,.8,2,.15);
    w.spawnParticle(Particle.SOUL_FIRE_FLAME,origin.clone().add(0,1,0),55,2,.8,2,.08);
    double damage=phase==1?10:phase==2?13:16;
    if(move.equals("RAGNAROK")){w.strikeLightningEffect(origin);damage=24;}
    if(move.equals("GENOCIDE")){body.setVelocity(dir.clone().multiply(1.65).setY(.42));w.spawnParticle(Particle.SWEEP_ATTACK,body.getLocation().add(0,1,0),18,1,.4,1,0);}
    else if(move.equals("TEMPEST")){w.spawnParticle(Particle.EXPLOSION_LARGE,origin.clone().add(0,1,0),3,1,.3,1,0);}
    if(move.equals("BLAST")||move.equals("TEMPEST")||move.equals("RAGNAROK")){
      double radius=move.equals("RAGNAROK")?14:move.equals("TEMPEST")?6:5;
      for(int i=0;i<60;i++){double angle=i*Math.PI/30;
        w.spawnParticle(Particle.CLOUD,origin.clone().add(Math.cos(angle)*radius,.4,Math.sin(angle)*radius),3,.1,.15,.1,.03);}
      w.spawnParticle(Particle.EXPLOSION_NORMAL,origin.clone().add(0,1,0),75,1,.5,1,.13);
      w.playSound(origin,Sound.ENTITY_GENERIC_EXPLODE,1.1f,.85f);
    }
    else w.spawnParticle(Particle.SWEEP_ATTACK,origin.clone().add(0,1,0),22,2,.5,2,.04);
    if(plugin instanceof jp.skillarena.SkillArenaPlugin arena)arena.trainingBots().damageInBossAttack(origin,dir,move,damage,body,party);
    Set<UUID> struck=new HashSet<>();for(Player p:players()){
      Vector delta=p.getLocation().toVector().subtract(origin.toVector()).setY(0);double along=delta.dot(dir);double sideways=delta.clone().subtract(dir.clone().multiply(along)).length();
      boolean hit=switch(move){case "BREAK"->along>=0&&along<=4&&sideways<1.5;case "BLAST"->delta.length()<5;case "TEMPEST"->delta.length()<6;case "GENOCIDE"->along>=0&&along<14&&sideways<2;default->delta.length()<14;};
      if(!hit||!struck.add(p.getUniqueId())||System.currentTimeMillis()<dodgeUntil.getOrDefault(p.getUniqueId(),0L))continue;
      if(!p.hasLineOfSight(body)&&!move.equals("TEMPEST"))continue;
      damagePlayer(p,damage,body);
      if(move.equals("BLAST")||move.equals("TEMPEST")||move.equals("RAGNAROK")){
        Vector away=p.getLocation().toVector().subtract(origin.toVector()).setY(0);
        if(away.lengthSquared()<.0001)away=dir.clone();
        away.normalize();double force=move.equals("RAGNAROK")?1.55:move.equals("TEMPEST")?1.25:1.05;
        p.setVelocity(new Vector(away.getX()*force,move.equals("RAGNAROK")?.85:.65,away.getZ()*force));
      }
      if(move.equals("BLAST")||move.equals("RAGNAROK"))p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,30,1));
    }
  }
  public void registerDodge(Player p,long until){dodgeUntil.put(p.getUniqueId(),until);}
  @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
  public void naturalMelee(EntityDamageByEntityEvent event){
    if(body!=null&&event.getDamager().getUniqueId().equals(body.getUniqueId())&&event.getEntity() instanceof Player){event.setCancelled(true);return;}
    if(echoes.containsKey(event.getDamager().getUniqueId())&&event.getEntity() instanceof Player)event.setCancelled(true);
  }
  @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
  public void preventSunBurn(EntityCombustEvent event){
    if(body!=null&&event.getEntity().getUniqueId().equals(body.getUniqueId())){event.setCancelled(true);return;}
    if(echoes.containsKey(event.getEntity().getUniqueId()))event.setCancelled(true);
  }
  @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void hurt(EntityDamageEvent event){
    Echo echo=echoes.get(event.getEntity().getUniqueId());
    if(echo!=null){
      event.setCancelled(true);
      if(!(event instanceof EntityDamageByEntityEvent by))return;
      Entity source=by.getDamager();
      if(source instanceof Projectile projectile&&projectile.getShooter() instanceof Player shooter)source=shooter;
      if(!(source instanceof Player player)||!party.contains(player.getUniqueId()))return;
      echo.hp=Math.max(0,echo.hp-Math.max(0,event.getDamage()));
      if(echo.hp<=0){Location at=echo.zombie.getLocation();echoes.remove(echo.zombie.getUniqueId());echo.zombie.remove();at.getWorld().spawnParticle(Particle.SOUL,at.add(0,1,0),80,.8,.8,.8,.08);at.getWorld().playSound(at,Sound.ENTITY_WITHER_HURT,1,1.5f);}
      else echo.zombie.setCustomName("§7剣帝の分身 §f"+(int)Math.ceil(echo.hp)+"/400");
      return;
    }
    if(body==null||!event.getEntity().getUniqueId().equals(body.getUniqueId()))return;
    if(!(event instanceof EntityDamageByEntityEvent)){event.setCancelled(true);return;}
    if(event instanceof EntityDamageByEntityEvent by){Entity source=by.getDamager();if(source instanceof Projectile projectile&&projectile.getShooter() instanceof Player shooter)source=shooter;
      if(source instanceof Player p){if(!party.contains(p.getUniqueId())){event.setCancelled(true);return;}recentThreat.put(p.getUniqueId(),System.currentTimeMillis()+4000);}}
    event.setCancelled(true);
    // Read pre-armor damage: netherite is purely cosmetic for Vargas.
    double amount=Math.max(0,event.getDamage());
    if(amount<=0)return;
    currentHp=Math.max(0,currentHp-amount);
    if(currentHp<=0)end(true);
  }
  @EventHandler public void death(EntityDeathEvent e){if(echoes.containsKey(e.getEntity().getUniqueId())){echoes.remove(e.getEntity().getUniqueId());e.getDrops().clear();return;}if(body!=null&&e.getEntity().getUniqueId().equals(body.getUniqueId())){e.getDrops().clear();end(true);}}
  @EventHandler public void quit(PlayerQuitEvent e){if(bar!=null)bar.removePlayer(e.getPlayer());}
  public void end(boolean win){clearEchoes();if(visual!=null){visual.close();visual=null;}if(ticker!=null){ticker.cancel();ticker=null;}if(bar!=null){bar.removeAll();bar=null;}if(body!=null){Location at=body.getLocation();if(win){at.getWorld().playSound(at,Sound.UI_TOAST_CHALLENGE_COMPLETE,1,1);for(UUID id:party){Player p=Bukkit.getPlayer(id);if(p!=null){p.sendTitle("§6討伐成功","§e剣帝ヴァルガス",10,55,15);if(plugin instanceof jp.skillarena.SkillArenaPlugin arena)arena.rewardBossAccessory(p);}}}else for(UUID id:party){Player p=Bukkit.getPlayer(id);if(p!=null)p.sendMessage("§cヴァルガス戦終了（参加者不在・中断）");}body.remove();body=null;}pending=false;skyfallActive=false;skyfallTarget=null;if(raidId!=null&&plugin instanceof jp.skillarena.SkillArenaPlugin arena)arena.raids().endRaid(raidId);raidId=null;party.clear();}
}
