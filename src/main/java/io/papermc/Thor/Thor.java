package io.papermc.Thor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * The {@code Thor} class is a Minecraft 1.21.11 plugin that grants the player the ability to utilize some of the superhero Thor's abilities.
 * @author Sharp
 * @version 1.0.0
 */
public class Thor extends JavaPlugin implements Listener {
  private final Material DIAMOND_AXE = Material.DIAMOND_AXE;
  private int activeStrikes = 0;
  private final int MAX_ACTIVE_STRIKES = 10;
  
  @Override
  public void onEnable() {
    Bukkit.getPluginManager().registerEvents(this, this);
  }

  /**
   * Events related to the player having a diamond axe in hand.
   * @param event
   */
  @EventHandler
  public void onPlayerHasDiamondAxe(PlayerInteractEvent event) {
    Action action = event.getAction();
    Player player = event.getPlayer();

    if (!isDiamondAxeInHand(event)) {
      return;
    }
    
    if (action.equals(Action.LEFT_CLICK_AIR)) {
      strikeLightningLocation(player);
    }

    if (action.equals(Action.RIGHT_CLICK_AIR)) {
      strikeLightningNearbyEntities(player);
    }
  }

  /**
   * Events related to sneaking with a diamond axe.
   * @param event
   */
  @EventHandler
  public void onPlayerSneakWithDiamondAxe(PlayerToggleSneakEvent event) {
    if (!isDiamondAxeInHand(event)) {
      return;
    }
    
    if (event.getPlayer().isSneaking()) {
      Player player = event.getPlayer();
      strikeLightningAroundPlayer(player);
    }
  }

  /**
   * Events related to the player throwing the diamond axe.
   * @param event
   */
  @EventHandler
  public void onPlayerThrowDiamondAxe(PlayerDropItemEvent event) {
    if (event.getItemDrop().getItemStack().getType() == this.DIAMOND_AXE) {
      throwAxeStrikeLightning(event);
    }
  }

  @EventHandler
  public void onPlayerDamageToLightning(EntityDamageEvent event) {
    if (event.getEntity() instanceof Player player) {
      if (event.getCause() == EntityDamageEvent.DamageCause.LIGHTNING) {
        Material itemInHand = player.getInventory().getItemInMainHand().getType();
        if (itemInHand == Material.DIAMOND_AXE) {
          event.setCancelled(true);
        }
      }
    }
  }

  /**
   * Strikes lightning at the targeted location. The player <b>must</b> be holding a diamond axe. The lightning strike can also hit the air.<br>
   * The <b>maxDistance</b> can be any arbitrary <b>Integer</b>, and it represents the distance between the lightning strike and the player is; this can be freely changed.
   * @param player The player holding the axe.
   */
  private void strikeLightningLocation(Player player) {
    RayTraceResult result = player.getWorld().rayTraceBlocks(
      player.getEyeLocation(), 
      player.getEyeLocation().getDirection(), 
      256
    );

    Location strikeLocation;
    
    if (result != null && result.getHitBlock() != null) {
        strikeLocation = result.getHitPosition().toLocation(player.getWorld());
      } else {
        strikeLocation = player.getEyeLocation().add(
          player.getEyeLocation().getDirection().multiply(50)
        );
      }
      player.getWorld().strikeLightning(strikeLocation);
  }

  /***
   * Strikes lightning around the player.
   * @param player The player holding the axe.
   */
  private void strikeLightningAroundPlayer(Player player) {
    Location base = player.getLocation();
    World world = player.getWorld();

    for (int i = 0; i < 4; i++) {
      if (activeStrikes < MAX_ACTIVE_STRIKES) {
        strikeLightningOnSneakNearPlayer(world, base, i);
        activeStrikes++;
        
        new BukkitRunnable() {
          @Override
          public void run() {
            activeStrikes--;
          }
        }.runTaskLater(Thor.this, 20L);
      }
    }
    world.strikeLightning(base.clone().add(0, 0, 2));
  }

  /**
   * Strikes lightning at all entities within <b>75 blocks</b> of the player in all directions except the y-axis, which is set to <b>25 blocks</b>.
   * @param player The player holding the axe.
   */
  private void strikeLightningNearbyEntities(Player player) {
    World world = player.getWorld();

    for (Entity e : player.getNearbyEntities(75, 25, 75)) {
      if (activeStrikes < MAX_ACTIVE_STRIKES) {
        world.strikeLightning(e.getLocation());
        activeStrikes++;
        
        new BukkitRunnable() {
          @Override
          public void run() {
            activeStrikes--;
          }
        }.runTaskLater(Thor.this, 20L);
      }
    }
  }
  
  /**
   * Strikes lightning at the landing location of the diamond axe and returns back to the player.
   * @param event
   */
  private void throwAxeStrikeLightning(PlayerDropItemEvent event) {
    Item axe = event.getItemDrop();
    Player player = event.getPlayer();
    Vector direction = player.getLocation().getDirection().normalize().multiply(1.5);

    direction.setY(0.5 + (direction.getY() - 0.2));
    axe.setVelocity(direction);
    axe.setInvulnerable(true);
    axe.setGravity(true);

    new BukkitRunnable() {
      boolean struck = false;

      @Override
      public void run() {
        if (!axe.isValid() || !player.isOnline()) {
          this.cancel();
          return;
        }
        axe.setPickupDelay(Integer.MAX_VALUE);

        if (axe.getLocation().getBlock().getType() != Material.AIR || axe.isOnGround()) {
          if (activeStrikes < MAX_ACTIVE_STRIKES && !struck) {
            activeStrikes++;
            
            Location strikeLocation = axe.getLocation();
            player.getWorld().strikeLightning(strikeLocation);
            struck = true;
            new BukkitRunnable() {
              @Override
              public void run() {
                  activeStrikes--;
              }
            }.runTaskLater(Thor.this, 20L);
          }
          player.getInventory().addItem(axe.getItemStack());
          axe.remove();
          this.cancel();
        }
      }
    }.runTaskTimer(this, 0L, 1L);
  }

  /**
   * Strikes lightning two blocks away from the player in the four cardinal directions.<br>
   * The passed in-value of the {@code direction} parameter can be any of and represent the following with respect to the player:<br>
   * - 0: Front<br>
   * - 1: Behind<br>
   * - 2: Left<br>
   * - 3: Right<br>
   * @param world
   * @param base
   * @param direction
   * @param isFrontOrLeft
   */
  private void strikeLightningOnSneakNearPlayer(World world, Location base, int direction) {
    switch (direction) {
      case 0 ->
        world.strikeLightning(base.clone().add(2, 0, 0));
      case 1 ->
        world.strikeLightning(base.clone().add(-2, 0, 0));
      case 2 ->
        world.strikeLightning(base.clone().add(0, 0, 2));
      case 3 ->
        world.strikeLightning(base.clone().add(0, 0, -2));
    }
  }

  /**
   * Checks if the currently held item is a Diamond Axe.
   * @param event
   * @return <b>True</b> if the currently held item is a Diamond Axe.
  */
  private boolean isDiamondAxeInHand(PlayerEvent event) {
    return getMainHandItem(event) == this.DIAMOND_AXE;
  }

  /**
   * Gets the item being currently held by the player in their right hand.
   * @param event
   * @return The item being held by the player.
   */
  private Material getMainHandItem(PlayerEvent event) {
    return event.getPlayer().getInventory().getItemInMainHand().getType();
  }
}