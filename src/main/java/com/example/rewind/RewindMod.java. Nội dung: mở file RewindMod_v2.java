package com.example.rewind.mixin;

import com.example.rewind.RewindMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"))
    private void rewind$record(BlockPos pos, BlockState state, int flags, int depth,
                               CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerLevel sl) {
            RewindMod.record(sl, pos.immutable());
        }
    }
}
