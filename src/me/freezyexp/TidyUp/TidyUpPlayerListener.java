package me.freezyexp.TidyUp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

import com.griefcraft.lwc.LWC;
import com.griefcraft.lwc.LWCPlugin;
import com.griefcraft.model.Protection;

public class TidyUpPlayerListener implements Listener {

	public class updatePlayerInventory implements Runnable {
		private final Player player;

		public updatePlayerInventory(Player player) {
			this.player = player;
		}

		@SuppressWarnings("deprecation")
		@Override
		public void run() {
			try {
				player.updateInventory();
			} catch (Exception e) {

			}
		}
	}

	public final TidyUp tidyMain;
	public final LWC lwc;

	private class TidyTracker {
		public final boolean OCD;
		public final int startedWith;

		public TidyTracker(final int amount, final boolean ocd) {
			startedWith = amount;
			OCD = ocd;
		}

		public int storeCounter = 0;
		public int validTargets = 0;
	}

	public TidyUpPlayerListener(TidyUp tidyUp) {
		tidyMain = tidyUp;

		Plugin lwcPlugin = tidyUp.pm.getPlugin("LWC");
		if (lwcPlugin != null) {
			lwc = ((LWCPlugin) lwcPlugin).getLWC();
		} else {
			tidyMain.log.log(Level.SEVERE, "LWC was not detected");
			lwc = null;
		}
	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		Action action = event.getAction();
		if (action != Action.LEFT_CLICK_BLOCK)
			return;

		Block block = event.getClickedBlock();
		Material type = block.getType();
		// tidyMain.log.log(Level.INFO, "checking "+ i);
		if (type != Material.CHEST && type != Material.TRAPPED_CHEST)
			return; // only chests

		Player player = event.getPlayer();

		ItemStack hand = player.getItemInHand();
		if (hand != null && hand.getAmount() > 0)
			return; // only empty hands

		// player.sendMessage("§dTidyUp check");

		if (tidyMain.getOCD(player) == TidyUp.OCD.False)
			return;

		World world = block.getWorld();
		Location loc = block.getLocation();
		Protection check = lwc.getPhysicalDatabase().loadProtection(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

		if (check != null && !lwc.canAccessProtection(player, check))
			return;

		BlockState bs = block.getState();

		Inventory inventory = null;
		boolean doubleChest = false;
		if (bs instanceof DoubleChest) {
			inventory = ((DoubleChest) bs).getInventory();
			doubleChest = true;
		} else if (bs instanceof Chest) {
			inventory = ((Chest) bs).getInventory();
		}

		if (inventory != null) {
			int invSize = inventory.getSize();

			if (doubleChest) {
				player.sendMessage("§aTidyUp is OCD'ing the §2DoubleChest");
			} else if (invSize == 54 && !doubleChest) {
				player.sendMessage("§aTidyUp is OCD'ing the DoubleChest");
				doubleChest = true;
			} else {
				player.sendMessage("§aTidyUp is OCD'ing the Chest");
			}
			OCD(inventory);

			if (doubleChest) {
				DoubleChest holder = (DoubleChest) inventory.getHolder();
				InventoryHolder h1;
				h1 = holder.getLeftSide();
				h1.getInventory().setContents(h1.getInventory().getContents());

				h1 = holder.getRightSide();
				h1.getInventory().setContents(h1.getInventory().getContents());
			}
			bs.update();

			if (doubleChest) {
				Chest c = attached(block);
				if (c != null)
					c.update();
			}

			// Deprecated, but no replacement
			schedulePlayerUpdate(player);
		}
	}

	@EventHandler
	public void onPlayerDropItem(PlayerDropItemEvent event) {
		Player player = event.getPlayer();

		TidyUpPlayerData data = new TidyUpPlayerData();

		if (tidyMain.getTidyUpOn(player, data)) {
			Item item = event.getItemDrop();
			item.setPickupDelay(TidyUp.pickupDelay * 10);

			if (handleItemDrop(item, player, data)) {
				schedulePlayerUpdate(player);
			}
		}
	}

	/**
	 * @param player
	 */
	public void schedulePlayerUpdate(Player player) {
		try {
			BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
			scheduler.runTask(tidyMain, new updatePlayerInventory(player));
		} catch (Exception e) {

		}
	}

	/**
	 * @param event
	 * @param player
	 * @param data
	 */
	private boolean handleItemDrop(Item item, Player player, TidyUpPlayerData data) {
		ItemStack itemStack = item.getItemStack();
		Material mat = itemStack.getType();
		World world = player.getWorld();
		Location loc = player.getLocation();

		List<Protection> prots = lwc.getPhysicalDatabase().loadProtections(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), TidyUp.radius);

		// convert item stack to what chest returns
		HashMap<Integer, ItemStack> hashStack = new HashMap<Integer, ItemStack>();
		hashStack.put(0, itemStack);

		TidyTracker tt = new TidyTracker(itemStack.getAmount(), data.OCD == TidyUp.OCD.Auto);

		for (int i = 0; i < prots.size(); i++) {
			Protection check = prots.get(i);

			Block block = check.getBlock();
			Material type = block.getType();
			// tidyMain.log.log(Level.INFO, "checking "+ i);
			if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
				boolean access = lwc.canAccessProtection(player, check);
				switch (data.on) {
				case None:
					// invalid
					break;
				case Access:
					if (access) {
						// allowed
						break;
					}
					continue;
				case AnySneak:
					if (player.isSneaking()) {
						// allowed
						break;
					} else if (access) {
						// allowed
						break;
					}
					continue;
				case AnyStand:
					if (player.isSneaking()) {
						if (!access)
							continue;
						// allowed
						break;
					}
					break;
				case Own:
					if (check.isOwner(player))
						break;
					else
						continue;
				}

				tt.validTargets++;

				BlockState bs = block.getState();
				if (bs instanceof Chest) {
					Chest chest = (Chest) bs;

					int stuffit = stuffItIn(false, tt, mat, chest, hashStack);
					if (stuffit < 0) {
						item.remove();
						break;
					}

					chest = attached(block);
					if (chest != null) {
						int secondStuffit = stuffItIn(stuffit == 1, tt, mat, chest, hashStack);
						if (secondStuffit == 0) {
							continue;
						}
						if (secondStuffit < 0) {
							item.remove();
							break;
						}
					}
				}
			}
		}

		if (tt.storeCounter > 0) {
			player.sendMessage("§a[TidyUp]:§fDropped item(s) were stored in: " + tt.storeCounter + " nearby chests.");
			if (tt.OCD)
				player.sendMessage("§fand those chests were sorted.");
		}

		int leftover = 0;
		if (!hashStack.isEmpty())
			leftover = hashStack.get(0).getAmount();

		if (leftover == 0 || item.isDead()) {
			if (!item.isDead()) {
				player.sendMessage("§a[TidyUp]§f killed a ghost stack");
				tidyMain.log.log(Level.INFO, "removed a ghost stack");
				item.remove();
			}
			return true;
		}

		if (tt.validTargets > 0) {
			int stored = tt.startedWith - leftover;
			if (stored > 0) {
				player.sendMessage("§fManaged to store: §3" + stored + "§f, leaving " + itemStack.getAmount() + " up to you.");
			} else {
				player.sendMessage("§a[TidyUp]:§f nearby LWC chests full or do not contain that item.");
			}
			return stored > 0;
		}
		return false;
	}

	private int stuffItIn(final boolean stuffit, final TidyTracker tt, final Material mat, final Chest chest, HashMap<Integer, ItemStack> hashStack) {
		Inventory inventory = chest.getInventory();

		// check type
		if (inventory.contains(mat) || stuffit) {
			int input = hashStack.get(0).getAmount();
			hashStack = inventory.addItem(hashStack.get(0));

			int out = 0;
			if (!hashStack.isEmpty())
				out = hashStack.get(0).getAmount();

			if (out != input) {
				tt.storeCounter++;
				if (tt.OCD) {
					OCD(inventory);
				}
			}

			if (hashStack.isEmpty() || out == 0) {
				return -1;
			}
			return 1;
		}
		return 0;
	}

	private void OCD(Inventory inventory) {
		if (inventory != null) {
			ArrayList<ItemStack> items = new ArrayList<ItemStack>(Arrays.asList(inventory.getContents()));
			Collections.sort(items, new OCDComparator());

			for (int x = 0; x < items.size(); x++) {
				ItemStack i = items.get(x);
				if (i != null) {
					int a = i.getAmount();
					if (a == 0) {
						items.remove(x);
						x--;
					} else {
						if (x + 1 >= items.size())
							continue;

						ItemStack next = items.get(x + 1);
						if (!i.isSimilar(next))
							continue;

						Material m1 = i.getType();
						int max = m1.getMaxStackSize();

						int plus = a + next.getAmount();
						if (plus > max) {
							i.setAmount(max);
							next.setAmount(plus - max);
						} else {
							i.setAmount(plus);
							a = plus;
							items.remove(x + 1);

							if (plus < max)
								x--;// redo this step, to check the
									// next stacks as well
						}
					}
				}
			}
			inventory.clear();
			inventory.setContents(items.toArray(new ItemStack[items.size()]));
		}
	}

	public class OCDComparator implements Comparator<ItemStack> {
		@Override
		public int compare(ItemStack o1, ItemStack o2) {
			if (o1 == null) {
				if (o2 == null)
					return 0;
				else
					return 1;
			} else if (o2 == null) {
				return -1;
			}

			Material m1 = o1.getType();
			Material m2 = o2.getType();

			if (o1.isSimilar(o2)) {
				return Double.compare(o1.getAmount(), o2.getAmount());
			} else
				return m1.compareTo(m2);
		}
	}

	// BetterChest internals
	private static final BlockFace[] FACES = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };

	private Chest attached(Block block) {
		// Find the first adjacent chest. Note: hacking of various sorts/degrees
		// and/or
		// other plugins might allow multiple chests to be adjacent. Deal with
		// that later
		// if it really becomes necessary (and at all possible to detect).

		for (BlockFace face : FACES) {
			Block other = block.getRelative(face);
			if (other.getType() == Material.CHEST) {
				return (Chest) other.getState(); // Found it.
			}
		}
		return null; // No other adjacent chest.
	}
}
