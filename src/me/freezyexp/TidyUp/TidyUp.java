package me.freezyexp.TidyUp;

import java.util.logging.Logger;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class TidyUp extends JavaPlugin
{
	public PluginManager pm;
	public Logger log;

	private TidyUpPlayerListener playerListener;	
	
	protected FileConfiguration config;
	
	public int radius = 10;
	public int pickupDelay = 3;
	
	@Override
	public void onDisable() {		
		config.set("pickupRadius", radius);
		config.set("playerPickupDelay", pickupDelay);
		saveConfig();
	}

	@Override
	public void onEnable() {
		pm = this.getServer().getPluginManager();
		log = Logger.getLogger("Minecraft");
		playerListener = new TidyUpPlayerListener(this); 
		config = getConfig();
		
		radius 		= config.getInt("pickupRadius", 10);		
		pickupDelay = config.getInt("playerPickupDelay", 3);
		
		pm.registerEvents(playerListener, this);		
	}

}
