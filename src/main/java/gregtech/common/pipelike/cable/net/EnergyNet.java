package gregtech.common.pipelike.cable.net;

import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.unification.material.properties.WireProperties;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class EnergyNet extends PipeNet<WireProperties> {

    private long lastEnergyFluxPerSec;
    private long energyFluxPerSec;
    private long lastTime;

    private final Map<BlockPos, List<EnergyRoutePath>> NET_DATA = new Object2ObjectOpenHashMap<>();

    protected EnergyNet(WorldPipeNet<WireProperties, EnergyNet> world) {
        super(world);
    }

    public List<EnergyRoutePath> getNetData(BlockPos pipePos) {
        List<EnergyRoutePath> data = NET_DATA.get(pipePos);
        if (data == null) {
            data = EnergyNetWalker.createNetData(getWorldData(), pipePos);
            if (data == null) {
                // walker failed, don't cache so it tries again on next insertion
                return Collections.emptyList();
            }
            data.sort(Comparator.comparingInt(EnergyRoutePath::getDistance));
            NET_DATA.put(pipePos, data);
        }
        return data;
    }

    public long getEnergyFluxPerSec() {
        World world = getWorldData();
        if (world != null && !world.isRemote && (world.getTotalWorldTime() - lastTime) >= 20) {
            lastTime = world.getTotalWorldTime();
            clearCache();
        }
        return lastEnergyFluxPerSec;
    }

    public void addEnergyFluxPerSec(long energy) {
        energyFluxPerSec += energy;
    }

    public void clearCache() {
        lastEnergyFluxPerSec = energyFluxPerSec;
        energyFluxPerSec = 0;
    }

    @Override
    public void onNeighbourUpdate(BlockPos fromPos) {
        NET_DATA.clear();
    }

    @Override
    public void onPipeConnectionsUpdate() {
        NET_DATA.clear();
    }

    @Override
    public void onChunkUnload() {
        NET_DATA.clear();
    }

    @Override
    protected void transferNodeData(Map<BlockPos, Node<WireProperties>> transferredNodes,
                                    PipeNet<WireProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        NET_DATA.clear();
        ((EnergyNet) parentNet).NET_DATA.clear();
    }

    @Override
    protected void writeNodeData(WireProperties nodeData, NBTTagCompound tagCompound) {
        tagCompound.setLong("voltage", nodeData.getVoltage());
        tagCompound.setInteger("amperage", nodeData.getAmperage());
        tagCompound.setInteger("loss", nodeData.getLossPerBlock());
    }

    @Override
    protected WireProperties readNodeData(NBTTagCompound tagCompound) {
        long voltage = tagCompound.getLong("voltage");
        int amperage = tagCompound.getInteger("amperage");
        int lossPerBlock = tagCompound.getInteger("loss");
        return new WireProperties(voltage, amperage, lossPerBlock);
    }
}
