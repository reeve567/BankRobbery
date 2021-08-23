package dev.reeve.bankrobbery;

import org.bukkit.Location;

public class Config {
	public double stolenAmountPerPerson = 250.0;
	public long doorTime = 8;
	public long survivalTime = 120;
	public long vaultTime = 15;
	public long robberyCooldown = 1800;
	public String bankRegion = "";
	public String vaultRegion = "";
	public String world = "";
	public Location robberySignLocation;
	public Location keypadLocation;
	public Location doorLocation;
	public Messages messages = new Messages();
}
