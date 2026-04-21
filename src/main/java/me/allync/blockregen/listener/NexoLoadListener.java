package me.allync.blockregen.listener;

import com.nexomc.nexo.api.events.NexoItemsLoadedEvent;
import me.allync.blockregen.BlockRegen;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class NexoLoadListener implements Listener {

	private final BlockRegen plugin;

	public NexoLoadListener(BlockRegen plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onNexoItemsLoaded(NexoItemsLoadedEvent event) {
		plugin.getLogger().info("Nexo items loaded. Reloading BlockRegen blocks...");
		if (plugin.getBlockManager() != null) {
			plugin.getBlockManager().loadBlocks();
		}
	}
}

