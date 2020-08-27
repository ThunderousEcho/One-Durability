package oliner.charles.onedurability;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

public class OneDurability extends JavaPlugin implements Listener {
	
	private FileConfiguration config;
	
	@Override
	public void onEnable() {
		
		Bukkit.getPluginManager().registerEvents(this, this);
		
		sanitizeCraftingRecipies();
		
		config = this.getConfig();
		config.addDefault("affectsEnderEyes", true);
		config.addDefault("affectsBuckets", false);
		config.addDefault("affectsMobs", true);
		config.options().copyDefaults(true);
		saveConfig();
	}
	
	private void sanitizeCraftingRecipies() { //todo: test this
		
		Iterator<Recipe> it = getServer().recipeIterator();
		ArrayList<ShapedRecipe> toOverride = new ArrayList<ShapedRecipe>();
	    while(it.hasNext()) {
	    	Recipe recipe = it.next();
	    	if (recipe instanceof ShapedRecipe && isDamageable(recipe.getResult())) {
	    		toOverride.add((ShapedRecipe)recipe);
	    	}
	    }
	    
	    for (ShapedRecipe recipe : toOverride) {
	    	
	    	ItemStack itemStack = recipe.getResult().clone();
	    	String result = sanitize(itemStack);
	    	if (result != null) {
    			getLogger().info("changing crafting recipie for " + result);
    		}
	    	
	    	ShapedRecipe newRecipe  = new ShapedRecipe(recipe.getKey(), itemStack);
	    	newRecipe.shape(recipe.getShape());
	    	for (Map.Entry<Character, RecipeChoice> entry: recipe.getChoiceMap().entrySet()) {
	    		newRecipe.setIngredient(entry.getKey(), entry.getValue());
	    	}
	    	
	    	getServer().removeRecipe(recipe.getKey());
	    	getServer().addRecipe(newRecipe);
	    }
	}
	
	@EventHandler
	public void onPickupItem(EntityPickupItemEvent e) {
		final String result = sanitize(e.getItem().getItemStack());
		if (result != null) {
			getLogger().info("item picked up: " + result);
		}
	}
	
	@EventHandler
	public void onInventoryOpen(InventoryOpenEvent e) {
		final Inventory inventory = e.getInventory();
		final ArrayList<String> results = new ArrayList<String>();
		for (int i = 0; i < inventory.getSize(); i++) {
			final String result = sanitize(inventory.getItem(i));
			if (result != null) {
				results.add(result);
			}
		}
		if (results.size() != 0) {
			getLogger().info("inventory opened: " + String.join(", ", results));
		}
	}
	
	/*doesnt work for multiple items
	@EventHandler
	public void onCraftItem(CraftItemEvent e) {
		final String result = sanitize(e.getCurrentItem());
		if (result != null) {
			getLogger().info("item crafted: " + result);
		}
	}*/
	
	@EventHandler
	public void onItemMend(PlayerItemMendEvent e) {
		getLogger().info("" + e.getItem().getType() +  " mending cancelled");
		e.setCancelled(true);
	}
	
	@EventHandler
	public void onItemDamage(PlayerItemDamageEvent e) { //this is a failsafe.
		getLogger().info("damage applied to " + e.getItem().getType() + " increased to 10000");
		e.setDamage(10000);
	}
	
	@EventHandler
	public void onEntitySpawn(EntitySpawnEvent e) {
		if (config.getBoolean("affectsEnderEyes") && e.getEntity() instanceof EnderSignal) {
			getLogger().info("ender eye at " + e.getLocation() + " set to break");
			((EnderSignal)e.getEntity()).setDropItem(false);
		}
	}
	
	@EventHandler
	public void onEmptyBucket(PlayerBucketEmptyEvent event) {
		
		if (config.getBoolean("affectsBuckets")) {
		
			final ItemStack itemStack = event.getItemStack();
			final Player player = event.getPlayer();
			
			getLogger().info("breaking bucket for " + player.getName());
			
			playBreakEffect(player, itemStack);
			
			new BukkitRunnable() {
				public void run() {
					player.getInventory().removeItem(itemStack);
				}
			}.runTaskLater(this, 1);
		
		}
	}
	
	@EventHandler
	public void onProjectileLaunch(ProjectileLaunchEvent e) {
		
		if (config.getBoolean("affectsMobs")) {
			
			if (e.getEntity() instanceof AbstractArrow) {
				
				final ProjectileSource shooter =  e.getEntity().getShooter();
				
				if (shooter instanceof Entity && isLivingNonPlayer((Entity)shooter)) {
					
					final LivingEntity livingEntity = (LivingEntity)shooter;
					final ItemStack itemStack = livingEntity.getEquipment().getItemInMainHand();
					
					getLogger().info("breaking bow or crossbow " + itemStack.getType() + " for " + livingEntity.getName());
					
					livingEntity.getEquipment().setItemInMainHand(null);
					playBreakEffect(livingEntity, itemStack);
				}
			}
		}
	}
	
	@EventHandler
	public void onEntityDamage(EntityDamageEvent e) {
		
		if (config.getBoolean("affectsMobs")) {
			
			if (isLivingNonPlayer(e.getEntity())) {
				
				getLogger().info("breaking armor for " + e.getEntity().getName());
				
				final LivingEntity livingEntity = (LivingEntity)e.getEntity();
				
				final ItemStack helmet = livingEntity.getEquipment().getHelmet();
				if (isDamageable(helmet)) {
					livingEntity.getEquipment().setHelmet(null);
					playBreakEffect(livingEntity, helmet);
				}
				
				final ItemStack chestplate = livingEntity.getEquipment().getChestplate();
				if (isDamageable(chestplate)) {
					livingEntity.getEquipment().setChestplate(null);
					playBreakEffect(livingEntity, chestplate);
				}
				
				final ItemStack leggings = livingEntity.getEquipment().getLeggings();
				if (isDamageable(leggings)) {
					livingEntity.getEquipment().setLeggings(null);
					playBreakEffect(livingEntity, leggings);
				}
				
				final ItemStack boots = livingEntity.getEquipment().getBoots();
				if (isDamageable(boots)) {
					livingEntity.getEquipment().setBoots(null);
					playBreakEffect(livingEntity, boots);
				}
			}
		}
	}
	
	@EventHandler
	public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
		
		if (config.getBoolean("affectsMobs")) {
		
			if (e.getCause() == DamageCause.ENTITY_ATTACK && isLivingNonPlayer(e.getEntity())) {
				
				getLogger().info("breaking weapon for " + e.getDamager().getType());
				
				final LivingEntity livingEntity = (LivingEntity)e.getDamager();
				final ItemStack itemStack = livingEntity.getEquipment().getItemInMainHand();
				
				if (isDamageable(itemStack)) {
					livingEntity.getEquipment().setItemInMainHand(null);
					playBreakEffect(livingEntity, itemStack);
				}
			}
		}
	}
	
	private static boolean isLivingNonPlayer(Entity entity) {
		return entity instanceof LivingEntity && entity.getType() != EntityType.PLAYER;
	}
	
	private static void playBreakEffect(LivingEntity livingEntity, ItemStack itemStack) {
		final World world = livingEntity.getWorld();
		final Location location = livingEntity.getEyeLocation();
		world.playSound(livingEntity.getEyeLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
		world.spawnParticle(Particle.ITEM_CRACK, location.add(location.getDirection()), 10, 0.3, 0.5, 0.3, 0, itemStack);
	}
	
	private static boolean isDamageable(ItemStack itemStack) {
		return itemStack != null && itemStack.getType().getMaxDurability() != 0;
	}
	
	private static String sanitize(ItemStack itemStack) {
		
		if (isDamageable(itemStack)) {
			
			final ItemMeta itemMeta = itemStack.getItemMeta();
			final int damageBefore = ((Damageable)itemMeta).getDamage();
			
			((Damageable)itemMeta).setDamage(itemStack.getType().getMaxDurability() - 1);
			itemStack.setItemMeta(itemMeta);
			
			return itemStack.getType().name() + " went from "
				+ damageBefore + " damage to "
				+ ((Damageable)itemMeta).getDamage() + " damage";
			
		} else {
			
			return null;
			
		}
	}
	
	/*doesn't work- durability gets randomized no matter what, and stuff doesn't usually take durability when worn by an entity
	@EventHandler
	public void onCreatureSpawn(CreatureSpawnEvent e) {
		
		final LivingEntity entity = e.getEntity();
		
		new BukkitRunnable() {
			public void run() {
				
				EntityEquipment equipment = entity.getEquipment();
				if (equipment != null) {
					
					ItemStack helmet = equipment.getHelmet().clone();
					ItemStack chestplate = equipment.getChestplate().clone();
					ItemStack leggings = equipment.getLeggings().clone();
					ItemStack boots = equipment.getBoots().clone();
					ItemStack mainHand = equipment.getItemInMainHand().clone();
					ItemStack offHand = equipment.getItemInOffHand().clone();
					
					getLogger().info("onCreatureSpawn " + entity.getType() + " "
						+ sanitize(helmet) + ", "
						+ sanitize(chestplate) + ", "
						+ sanitize(leggings) + ", "
						+ sanitize(boots) + ", "
						+ sanitize(mainHand) + ", "
						+ sanitize(offHand));
					
					if (mainHand != null) {
						ItemMeta itemMeta = mainHand.getItemMeta();
						if (mainHand.getType().getMaxDurability() != 0 && itemMeta instanceof Damageable) {
							getLogger().info("main hand damage is " + ((Damageable)itemMeta).getDamage());
						}
					}
					
					ItemStack h = new ItemStack(Material.DIAMOND_HELMET);
					ItemMeta m = h.getItemMeta();
					((Damageable)m).setDamage(h.getType().getMaxDurability() - 1);
					h.setItemMeta(m);
					
					equipment.setHelmet(h);
					equipment.setChestplate(chestplate);
					equipment.setLeggings(leggings);
					equipment.setBoots(boots);
					equipment.setItemInMainHand(mainHand);
					equipment.setItemInOffHand(offHand);
				}
			}
		}.runTaskLater(this, 1);
	}*/
}
