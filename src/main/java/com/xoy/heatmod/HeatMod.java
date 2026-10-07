package com.xoy.heatmod;

import com.xoy.heatmod.network.HeatNetwork;
import net.minecraftforge.fml.common.Mod;

@Mod(HeatMod.MODID)
public class HeatMod {
    public static final String MODID = "heatmod";

    public HeatMod() {
        HeatNetwork.init();
    }
}
