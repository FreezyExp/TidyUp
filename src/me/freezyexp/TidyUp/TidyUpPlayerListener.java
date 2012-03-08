package me.freezyexp.TidyUp;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.griefcraft.lwc.*;
import com.griefcraft.model.Protection;

public class TidyUpPlayerListener implements Listener {
	
	public TidyUp tidyMain;	
	public LWC lwc;	
	
	public TidyUpPlayerListener(TidyUp tidyUp) {
		tidyMain = tidyUp;
		
		Plugin lwcPlugin = tidyUp.pm.getPlugin("LWC");
		if(lwcPlugin != null) {
		    lwc = ((LWCPlugin) lwcPlugin).getLWC();
		}
		else
		{
			tidyMain.log.log(Level.SEVERE, "LWC was not detected");			
		}
	}
	
	@EventHandler
	public void onPlayerDropItem(PlayerDropItemEvent event)
	{		
		Item item = event.getItemDrop();
		item.setPickupDelay(tidyMain.pickupDelay * 10);
		
		int matId = item.getItemStack().getType().getId();
		
		Player player = event.getPlayer();		
		
		World world = item.getWorld();
		Location loc = item.getLocation();
		
		List<Protection> prots = lwc.getPhysicalDatabase().loadProtections(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), tidyMain.radius);
		//check for nearby chests of this player
		//lwc.depositItems(putItInMe , item.getItemStack());
					
		//convert item stack to what chest returns
		HashMap<Integer, ItemStack> hashStack = new HashMap<Integer, ItemStack>();
		hashStack.put(0, item.getItemStack());
		
		//tidyMain.log.log(Level.INFO, "size " + prots.size());
		
		for(int i=0; i < prots.size(); i++)
		{			
			Protection check = prots.get(i);
			Block block = check.getBlock();			
			Material type = block.getType();			
			//tidyMain.log.log(Level.INFO, "checking "+ i);
			if(type == Material.CHEST )
			{		
				//tidyMain.log.log(Level.INFO, "material chest");
				BlockState bs = block.getState();
				if(bs instanceof Chest)
				{
					//tidyMain.log.log(Level.INFO, "instance is chest");
					Chest chest = (Chest)bs;									
					
					boolean stuffIt = false;
					
					//check type
					if(chest.getInventory().contains(matId))
					{
						stuffIt = true;
						
						//tidyMain.log.log(Level.INFO, "container found");					
						hashStack = chest.getInventory().addItem(hashStack.get(0));
						chest.update();
						
						if(hashStack.isEmpty())
						{
							//tidyMain.log.log(Level.INFO, "done with item drop");
							//we are done
							item.remove();
							return;
						}
					}								
					
					chest = attached(block);
					
					if(chest != null)
					{
						//tidyMain.log.log(Level.INFO, "double container found");
						if(chest.getInventory().contains(matId) || stuffIt)
						{
							//tidyMain.log.log(Level.INFO, "double container contained");						
							hashStack = chest.getInventory().addItem(hashStack.get(0));
							chest.update();
						}
						
						if(hashStack.isEmpty())
						{
							//tidyMain.log.log(Level.INFO, "done with item drop");
							//we are done
							item.remove();
							return;
						}	
					}												
				}
			}
		}
		
		if(prots.size() > 0 && !hashStack.isEmpty() && hashStack.get(0).getAmount() > 0)
		{
			player.sendMessage("Chests are full, or the item you just dropped was not in any of them");
			//tidyMain.log.log(Level.INFO, "Stacks remained, a chest is full");
			item.setItemStack(hashStack.get(0));
		}
	}
	

	// BetterChest internals
    private static final BlockFace[] FACES = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };
	
    private Chest attached(Block block) {
        // Find the first adjacent chest. Note: hacking of various sorts/degrees and/or
        // other plugins might allow multiple chests to be adjacent. Deal with that later
        // if it really becomes necessary (and at all possible to detect).
        
        for (BlockFace face : FACES) {
            Block other = block.getRelative(face);
            if (other.getType() == Material.CHEST) {
                return (Chest) other.getState();    // Found it.
            }
        }
        return null;    // No other adjacent chest.
    }
}
