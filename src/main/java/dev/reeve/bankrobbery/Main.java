package dev.reeve.bankrobbery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Logger;

public class Main extends JavaPlugin {
	private static final Logger log = Logger.getLogger("Minecraft");
	public static Main instance;
	public static HashMap<UUID, RobberyPhase> robberyPhaseHashMap = new HashMap<>();
	public static Date lastRobbery = new Date(0);
	public static HashMap<UUID, Double> amountTaken = new HashMap<>();
	public static Player commandUser = null;
	public static RobberyCommandSetting commandSetting = null;
	private final Gson gson = new GsonBuilder()
			.registerTypeAdapter(Location.class, GsonUtility.locationJsonDeserializer)
			.registerTypeAdapter(Location.class, GsonUtility.locationJsonSerializer)
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();
	private final File configFile = new File(getDataFolder(), "config.json");
	private Config config;
	private ProtectedRegion bankRegion;
	private ProtectedRegion vaultRegion;
	private InteractionListener interactionListener;
	private DeathListener deathListener;
	private Economy economy;
	
	public static String convert(String string) {
		return ChatColor.translateAlternateColorCodes('&', string);
	}
	
	private void runPlayerChecks() {
		new BukkitRunnable() {
			@Override
			public void run() {
				for (UUID uuid : robberyPhaseHashMap.keySet()) {
					Player player = Bukkit.getPlayer(uuid);
					if (player != null && player.isOnline()) {
						if (robberyPhaseHashMap.get(uuid) != RobberyPhase.ESCAPE) {
							if (!bankRegion.contains(BukkitAdapter.asBlockVector(player.getLocation()))) {
								robberyPhaseHashMap.remove(uuid);
								player.sendMessage(convert(config.messages.bankRobberyFailed));
								Bukkit.broadcastMessage(Main.convert(config.messages.bankIsSafe));
							}
							if (robberyPhaseHashMap.get(uuid) == RobberyPhase.VAULT) {
								if (!vaultRegion.contains(BukkitAdapter.asBlockVector(player.getLocation()))) {
									robberyPhaseHashMap.remove(uuid);
									player.sendMessage(convert(config.messages.bankRobberyFailed));
									Bukkit.broadcastMessage(Main.convert(config.messages.bankIsSafe));
								}
							}
						}
					} else {
						robberyPhaseHashMap.remove(uuid);
					}
				}
			}
		}.runTaskTimer(this, 10, 10);
	}
	
	private boolean setupEconomy() {
		if (getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			return false;
		}
		economy = rsp.getProvider();
		return true;
	}
	
	public void saveConfig() {
		if (config == null)
			config = new Config();
		
		try {
			FileWriter writer = new FileWriter(configFile);
			writer.write(gson.toJson(config));
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (command.getLabel().equalsIgnoreCase("bankrobbery")) {
			if (!(sender instanceof Player)) return false;
			
			if (args.length == 0) {
				sender.sendMessage("Incorrect arguments");
				return false;
			} else if (args[0].equalsIgnoreCase("keypad")) {
				sender.sendMessage("Please click on the keypad.");
				commandUser = (Player) sender;
				commandSetting = RobberyCommandSetting.KEYPAD;
			} else if (args[0].equalsIgnoreCase("door")) {
				sender.sendMessage("Please click on the door.");
				commandUser = (Player) sender;
				commandSetting = RobberyCommandSetting.DOOR;
			} else if (args[0].equalsIgnoreCase("sign")) {
				sender.sendMessage("Please click on the sign.");
				commandUser = (Player) sender;
				commandSetting = RobberyCommandSetting.SIGN;
			}
		} else if (command.getLabel().equalsIgnoreCase("vault")) {
			if (new Date().getTime() - lastRobbery.getTime() >= config.robberyCooldown * 1000) {
				sender.sendMessage(convert(config.messages.vaultReady));
			} else {
				long timeLeft = config.robberyCooldown - (new Date().getTime() - lastRobbery.getTime()) / 1000;
				String time = timeLeft / 60 + ":";
				if (timeLeft % 60 < 10) {
					time += "0";
				}
				time += timeLeft % 60;
				sender.sendMessage(convert(config.messages.vaultNotReady).replaceAll("%time%", time));
			}
		}
		return false;
	}
	
	@Override
	public void onEnable() {
		instance = this;
		
		if (!setupEconomy()) {
			log.severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		
		if (!getDataFolder().exists())
			getDataFolder().mkdir();
		
		if (configFile.exists()) {
			try {
				config = gson.fromJson(new FileReader(configFile), Config.class);
				
				if (config == null) {
					config = new Config();
					
					FileWriter writer = new FileWriter(configFile);
					writer.write(gson.toJson(new Config()));
					writer.close();
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			saveConfig();
		}
		
		if (config.world.equals("")) {
			log.severe(String.format("[%s] - No world has been picked in the config!", getDescription().getName()));
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		
		World world = Bukkit.getWorld(config.world);
		
		if (world == null) {
			log.severe(String.format("[%s] - An incorrect world has been picked in the config!", getDescription().getName()));
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		
		RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
		
		bankRegion = manager.getRegion(config.bankRegion);
		vaultRegion = manager.getRegion(config.vaultRegion);
		
		if (bankRegion == null || vaultRegion == null) {
			log.severe(String.format("[%s] - An incorrect region has been picked in the config!", getDescription().getName()));
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		
		interactionListener = new InteractionListener(this, economy, config);
		deathListener = new DeathListener(economy, config);
		
		Bukkit.getPluginManager().registerEvents(interactionListener, this);
		Bukkit.getPluginManager().registerEvents(deathListener, this);
		
		runPlayerChecks();
	}
}
