package dev.reeve.bankrobbery;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DeathListener implements Listener {
	
	private final Economy economy;
	private final Config config;
	
	public DeathListener(Economy economy, Config config) {
		this.economy = economy;
		this.config = config;
	}
	
	@EventHandler
	public void onDeath(PlayerDeathEvent e) {
		if (Main.robberyPhaseHashMap.containsKey(e.getEntity().getUniqueId())) {
			if (Main.robberyPhaseHashMap.get(e.getEntity().getUniqueId()) == RobberyPhase.ESCAPE) {
				for (Player player : Bukkit.getOnlinePlayers()) {
					if (player.getUniqueId() != e.getEntity().getUniqueId()) {
						economy.depositPlayer(player, Main.amountTaken.get(player.getUniqueId()));
					}
				}
			}
			Main.robberyPhaseHashMap.remove(e.getEntity().getUniqueId());
			Bukkit.broadcastMessage(Main.convert(config.messages.bankIsSafe));
		}
	}
}
