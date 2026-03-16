package com.coldmint.magicLeash;

import org.bukkit.plugin.java.JavaPlugin;

public final class MagicLeash extends JavaPlugin {

    LeashListener leashListener;
    @Override
    public void onEnable() {
        // Plugin startup logic
        leashListener = new LeashListener(this);
        getServer().getPluginManager().registerEvents(leashListener, this);

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if(leashListener != null){
            leashListener.clearAllRabbit();
        }
    }
}
