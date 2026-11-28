package com.ksp.mythos.model;

public final class RegistryTest {
    public static void main(String[] args) {
        var registry = new ModelRegistry();
        var asset = new ModelRegistry.Asset("mythos:dragon", "mythos:dragon_fallback",
                "mounts/dragon.glb", "a".repeat(64), 1024, 25000);
        registry.register(asset);
        if (!registry.find(asset.id()).orElseThrow().equals(asset)) throw new AssertionError();
        for (boolean client : new boolean[]{false, true})
            for (boolean verified : new boolean[]{false, true})
                if ((registry.selectMode(client, verified) == ModelRegistry.Mode.ENHANCED)
                        != (client && verified)) throw new AssertionError("Fallback failed");
        try { registry.register(asset); throw new AssertionError("Duplicate accepted"); }
        catch (IllegalArgumentException expected) {}
        try {
            new ModelRegistry.Asset("mythos:dragon", "mythos:dragon_fallback",
                    "../dragon.glb", "a".repeat(64), 1024, 25000);
            throw new AssertionError("Unsafe path accepted");
        } catch (IllegalArgumentException expected) {}
    }
}
