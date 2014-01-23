package me.freezyexp.TidyUp;

import java.util.logging.Logger;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.griefcraft.util.StringUtil;

public class TidyUp extends JavaPlugin {

	private static final String TIDYUP_ADMIN = "tidyup.admin";
	private static final String TIDYUP_CAN_GRAB = "tidyup.canGrab";
	private static final String TIDYUP_SET_GRAB = "tidyup.setGrab";
	private static final String TIDYUP_CAN_OCD = "tidyup.canOCD";
	private static final String TIDYUP_CAN_AUTO_OCD = "tidyup.canAutoOCD";

	public PluginManager pm;
	public Logger log;

	private TidyUpPlayerListener playerListener;

	protected FileConfiguration config;
	protected FileConfiguration perPlayer;
	protected ExtraConfig perPlayerConfig = new ExtraConfig(this, "players.yml");

	public static int radius = 10;
	public static int pickupDelay = 3;

	public static DropIn defaultDropIn = DropIn.Access;
	public static OCD defaultOCD = OCD.False;

	public enum DropIn {
		Own, Access, AnySneak, AnyStand, None;

		@Override
		public String toString() {
			return StringUtil.capitalizeFirstLetter(super.toString());
		}

		public static DropIn fromString(String text) {
			if (text != null) {
				for (DropIn b : DropIn.values()) {
					if (text.equalsIgnoreCase(b.toString())) {
						return b;
					}
				}
			}
			return defaultDropIn;
		}

		public static boolean getFromString(String text, TidyUpPlayerData data) {
			if (text != null) {
				for (DropIn b : DropIn.values()) {
					if (text.equalsIgnoreCase(b.toString())) {
						data.on = b;
						return true;
					}
				}
			}
			return false;
		}
	}
	
	public enum OCD {
		False ("Off"), True("Manual"), Auto("Automatic");
		
		private final String text;
		private OCD(final String text){
			this.text = text;			
		}
		
		@Override
		public String toString() {
			return text;
		}

		public static OCD fromString(String text) {
			final String first = text.substring(0,1).toUpperCase();
			if (text != null) {
				for (OCD b : OCD.values()) {
					if (b.toString().startsWith(first)) {
						return b;
					}
				}
			}
			return defaultOCD;
		}

		public static boolean getFromString(String text, TidyUpPlayerData data) {
			if (text != null) {
				for (OCD b : OCD.values()) {
					if (text.equalsIgnoreCase(b.toString())) {
						data.OCD = b;
						return true;
					}
				}
			}
			return false;
		}
	}

	@Override
	public void onDisable() {
		saveConfig();
	}

	@Override
	public void onEnable() {
		pm = this.getServer().getPluginManager();
		log = Logger.getLogger("Minecraft");
		playerListener = new TidyUpPlayerListener(this);

		config = getConfig();
		perPlayer = perPlayerConfig.getConfig();

		radius = config.getInt("pickupRadius", 10);
		pickupDelay = config.getInt("playerPickupDelay", 3);
		String dropType = config.getString("defaultDropIn", "Access");
		TidyUpPlayerData data = new TidyUpPlayerData();
		if (!DropIn.getFromString(dropType, data)) {
			log.severe("TidyUp error in config.yml, the defaultOn setting was not valid");
		}
		
		String sOCD = config.getString("defaultOCD");
		if(sOCD == null)
		{
			boolean bOCD = config.getBoolean("defaultOCD", false);
			defaultOCD = bOCD ? OCD.True : OCD.False;
		}
		else
		{
			defaultOCD = OCD.fromString(sOCD);
		}
		
		saveConfig();

		pm.registerEvents(playerListener, this);
	}

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (checkCommand(cmd.getName(), "tidyup")) {
			Player me = null;
			String playerName = null;
			boolean admin = false;
			boolean setGrab = false;
			OCD permitOCD = OCD.False;			

			if (sender instanceof Player) {
				me = (Player) sender;
				admin = me.hasPermission(TIDYUP_ADMIN);
				setGrab = me.hasPermission(TIDYUP_SET_GRAB);
				permitOCD = getMaxOCD(me);
				playerName = me.getName();
			} else {
				admin = true;
			}

			// doSomething
			if (args.length < 1) {
				SendHelp(sender, me);
			}

			if (args.length > 0) {
				// commands without playernames
				if (checkCommand(args[0], "reload")) {
					if (admin) {
						this.reloadConfig();
						sender.sendMessage("TidyUp Reloaded configs");
						return true;
					} else
						sendLackPermission(sender, "reload", TIDYUP_ADMIN);
				}
				
				if (admin)
				{
					// commands without playernames
					if (checkCommand(args[0], "defaultOCD")) {
						if (admin) {
							if(args.length > 1)
							{
								defaultOCD = OCD.fromString(args[1]);
								sender.sendMessage("TidyUp defaultOCD set to: "+ defaultOCD.toString());
								saveConfig();
							}
							else
								sender.sendMessage("TidyUp ocd "+ getOCDOptions());
							return true;
						} else
							sendLackPermission(sender, "defaultOCD", TIDYUP_ADMIN);
					}
					
				}

				// commands with player names,
				// first loop use current player
				// second loop (admin onlY) check for command on second
				// parameter
				int loopCheck = admin ? 2 : 1;
				for (int argOffset = 0; argOffset < loopCheck && args.length > argOffset; argOffset++) {
					TidyUpPlayerData data = new TidyUpPlayerData();

					String chk = args[argOffset];
					if (checkCommand(chk, "off")) {
						if (setGrab)
							if (setTidyUpOn(me.getName(), DropIn.None)) {
								sendStatus(sender, playerName, permitOCD != OCD.False);
							} else {
								sendErrorOnCommand(sender, playerName, permitOCD != OCD.False);
							}
						else
							sendLackPermission(sender, "off", TIDYUP_SET_GRAB);
						return true;
					}

					if (checkCommand(chk, "on")) {
						if (args.length > argOffset + 1) {
							if (setGrab) {
								if (DropIn.getFromString(args[argOffset + 1], data)) {
									if (setTidyUpOn(playerName, data.on)) {
										sendStatus(sender, playerName, permitOCD != OCD.False);
									} else {
										sendErrorOnCommand(sender, playerName, permitOCD != OCD.False);
									}
								}
							} else
								sendLackPermission(sender, "on", TIDYUP_SET_GRAB);
						} else
							sendDropInHelp(sender, admin);
						return true;
					}

					if (checkCommand(chk, "status")) {
						sendStatus(sender, playerName, permitOCD != OCD.False);
						return true;
					}

					if (checkCommand(chk, "ocd")) {
						if (permitOCD != OCD.False) {
							boolean res = false;
							if (args.length > argOffset + 1)
								res = setOCD(playerName, args[argOffset + 1]);
							else
								res = setOCD(playerName, null);

							if (res)
								sendStatus(sender, playerName, permitOCD != OCD.False);
							else
								sendErrorOnCommand(sender, playerName, permitOCD != OCD.False);

							return true;
						} else
							sendLackPermission(sender, "ocd", TIDYUP_CAN_OCD);
					}

					if (admin) {
						playerName = args[0];
					}
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public void reloadConfig() {
		super.reloadConfig();
		perPlayerConfig.reloadConfig();
		saveConfig();
	}

	@Override
	public void saveConfig() {
		if (config != null) {
			config.set("pickupRadius", radius);
			config.set("playerPickupDelay", pickupDelay);
			config.set("defaultOn", defaultDropIn.toString());
			config.set("defaultOCD", defaultOCD.toString());
		}
		super.saveConfig();
		if (perPlayerConfig != null)
			perPlayerConfig.saveConfig();
	}

	public boolean setTidyUpOn(String playerName, DropIn on) {
		String set = null;

		Server s = getServer();
		Player p = s.getPlayer(playerName);
		if (p != null) {
			set = p.getName();
		} else // search offline players
		{
			OfflinePlayer[] players = s.getOfflinePlayers();
			for (OfflinePlayer pl : players) {
				if (pl.getName().compareToIgnoreCase(playerName) == 0) {
					set = pl.getName();
				}
			}
		}

		if (set != null) {
			perPlayer.set(set + ".dropIn", on.toString());
			perPlayerConfig.saveConfig();
			return true;
		} else {
			return false;
		}
	}

	public String getTidyUpOn(String playerName) {
		String get = null;

		Server s = getServer();
		Player p = s.getPlayer(playerName);
		if (p != null) {
			get = p.getName();
		} else { // search known offline players
			OfflinePlayer[] players = s.getOfflinePlayers();
			for (OfflinePlayer pl : players) {
				if (pl.getName().compareToIgnoreCase(playerName) == 0) {
					get = pl.getName();
				}
			}
		}

		if (get != null) {
			return perPlayer.getString(get + ".dropIn", defaultDropIn.toString());
		} else {
			return null;
		}
	}

	/**
	 * get TidyUp data for player
	 * 
	 * @param player
	 *            the player
	 * @param on
	 *            per player chest type Setting (or default)
	 * @param OCD
	 *            per player OCD Setting (or default)
	 * @return true if player canGrab and has a chest type other then none
	 */
	public boolean getTidyUpOn(Player player, TidyUpPlayerData data) {
		if (player != null) {
			if (player.hasPermission(TIDYUP_CAN_GRAB)) {
				if (!DropIn.getFromString(perPlayer.getString(player.getName() + ".dropIn", defaultDropIn.toString()), data))
					data.on = defaultDropIn;

				data.OCD = getOCD(player);

				if (data.on == DropIn.None)
					return false;

				return true;
			}
		}
		return false;
	}

	private boolean setOCD(String playerName, String ocd) {
		String set = null;

		Server s = getServer();
		Player p = s.getPlayer(playerName);
		
		OCD maxOCD = defaultOCD;
		
		if (p != null) {
			set = p.getName();
			maxOCD = getMaxOCD(p);
		} else // search offline players
		{
			OfflinePlayer[] players = s.getOfflinePlayers();
			for (OfflinePlayer pl : players) {
				if (pl.getName().compareToIgnoreCase(playerName) == 0) {
					set = pl.getName();
				}
			}
		}				
		
		OCD newOCD = OCD.False;
		if (ocd == null) {
			// only console and Admin can call commands on offline players
			if (p == null || maxOCD != OCD.False ) {				
				ocd = perPlayer.getString(set + ".OCD");
				if(ocd == null)
				{
					newOCD = perPlayer.getBoolean(set + ".OCD", false) ? OCD.True : OCD.False;
				}
				else
				{					
					newOCD = OCD.fromString(ocd);
				}		
				
				switch(newOCD)
				{
					case Auto:
						newOCD = OCD.False;
						break;
					case False:
						if(maxOCD != OCD.False)
							newOCD = OCD.True;						
						break;
					case True:
						if(maxOCD == OCD.Auto)
							newOCD = OCD.Auto;
						else
							newOCD = OCD.False;
						break;
					default:
						newOCD = OCD.False;
						break;
				}
			}
		} else {
			newOCD = OCD.fromString(ocd);
			//check if we are just disabling, always allowed
			if(newOCD != OCD.False)
			{
				//check if setting is not the same as the max
				if(newOCD != maxOCD)
				{
					//if max is not absolute max (Auto), limit setting to the allowed max 
					if(maxOCD != OCD.Auto)
						newOCD = maxOCD;
				}
			}
		}

		if (set != null && ocd != null) {
			perPlayer.set(set + ".OCD", newOCD.toString());
			perPlayerConfig.saveConfig();
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Get the OCD status of a player
	 * 
	 * @param playerName
	 *            the playerName as known to the server
	 * @return true if the player has the OCD permission and the OCD setting set
	 *         to true
	 */
	private OCD getOCD(String playerName) {
		Server s = getServer();
		Player p = s.getPlayer(playerName);

		return getOCD(p);
	}

	protected OCD getOCD(Player player) {
		if (player != null) {
			OCD maxOCD = getMaxOCD(player);
			
			String sOCD = perPlayer.getString(player.getName() + ".OCD");
			if(sOCD == null)
			{
				if(defaultOCD == OCD.Auto && maxOCD == OCD.True)
					return maxOCD;
				return defaultOCD;
			}
			
			return OCD.fromString(sOCD);			
		}
		return OCD.False;
	}

	/**
	 * @param player
	 * @return
	 */
	public OCD getMaxOCD(Player player) {
		if(player.hasPermission(TIDYUP_CAN_AUTO_OCD))
			return OCD.Auto;
		if(player.hasPermission(TIDYUP_CAN_OCD))
			return OCD.True;
		return OCD.False;
	}

	// // HELPER FUNCTIONS ////

	public boolean checkCommand(String compare, String to) {
		return compare.equalsIgnoreCase(to);
	}

	public void sendLackPermission(CommandSender sender, String cmd, String permission) {
		sender.sendMessage("§cYou cannot use 'tidyup " + cmd + "' as you do not have the §f" + permission + "§c permission.");
	}

	public void SendHelp(CommandSender sender, Player me) {
		boolean admin = me.hasPermission(TIDYUP_ADMIN);

		sender.sendMessage("§2Usage:");
		if (me != null && admin) {
			sender.sendMessage("§f/tidyup [player] OCD §b"+ getOCDOptions() + " §3- toggle or set OCD setting");
			sender.sendMessage("§f/tidyup reload");
			sender.sendMessage("§f/TidyUp defaultOCD §b"+ getOCDOptions() + " §3- set default OCD setting for all players");
		}
		sendDropInHelp(sender, admin);
		sender.sendMessage("§f/tidyup off §3- disable tidyUp, same as TidyUp on None");
		sender.sendMessage("§f/tidyup OCD §b" + getOCDOptions() + " §3- organize LWC chests with empty hand punch or automatically on item drop");
		sender.sendMessage("§f/tidyup status §3- show current settings");
	}

	public void sendDropInHelp(CommandSender sender, boolean admin) {
		String opts = getDropInOptions();
		if (admin)
			sender.sendMessage("§f/tidyup §b<player> §fon " + opts + " §3- set behaviour type for [player]");
		sender.sendMessage("§f/tidyup on " + opts + " §3- set behaviour type");
	}

	/**
	 * @return
	 */
	public String getDropInOptions() {
		String opts = " §b[";
		DropIn[] ds = DropIn.values();
		for (int i = 0; i < ds.length - 1; i++)
			opts += ds[i].toString() + "§7|§b";
		opts += ds[ds.length - 1] + "] ";
		return opts;
	}
	
	/**
	 * @return
	 */
	public String getOCDOptions() {
		String opts = " §b[";
		OCD[] ds = OCD.values();
		for (int i = 0; i < ds.length - 1; i++)
			opts += ds[i].toString() + "§7|§b";
		opts += ds[ds.length - 1] + "] ";
		return opts;
	}

	public void sendErrorOnCommand(CommandSender sender, String playerName, boolean canOCD) {
		sender.sendMessage("§cTidyup could not execute command for player: §b" + playerName);
		sendStatus(sender, playerName, canOCD);
	}

	public boolean sendStatus(CommandSender sender, String playerName, boolean canOCD) {
		sender.sendMessage("§aTidyUp status for §b" + playerName);
		sender.sendMessage("§aon :§b " + getTidyUpOn(playerName));
		if (canOCD)
			sender.sendMessage("§aOCD :§b " + getOCD(playerName));
		return true;
	}
}