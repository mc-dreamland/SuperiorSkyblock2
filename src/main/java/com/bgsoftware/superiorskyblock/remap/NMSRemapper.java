package com.bgsoftware.superiorskyblock.remap;

import com.bgsoftware.common.annotations.Nullable;
import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.core.ServerVersion;
import com.bgsoftware.superiorskyblock.core.logging.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.neoforged.art.api.Renamer;
import net.neoforged.art.api.SignatureStripperConfig;
import net.neoforged.art.api.Transformer;
import net.neoforged.srgutils.IMappingFile;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.PluginClassLoader;

import java.io.File;
import java.io.FileReader;
import java.net.URISyntaxException;

public class NMSRemapper {

    private static final Gson GSON = new GsonBuilder().create();

    private final SuperiorSkyblockPlugin plugin;

    @Nullable
    private File cachedNMSFile = null;
    @Nullable
    private IMappingFile mappingFile = null;

    public NMSRemapper(SuperiorSkyblockPlugin plugin) {
        this.plugin = plugin;
        this.checkCachedNMSFile();
    }

    private void checkCachedNMSFile() {
        File nmsCacheFolder = new File(this.plugin.getDataFolder(), ".nms");

        if (!nmsCacheFolder.isDirectory())
            return;

        File nmsFile = new File(nmsCacheFolder, "nms.jar");

        if (!nmsFile.exists())
            return;

        File indexFile = new File(nmsCacheFolder, "index.json");

        if (!indexFile.exists())
            return;

        try (FileReader indexFileReader = new FileReader(indexFile)) {
            JsonObject index = GSON.fromJson(indexFileReader, JsonObject.class);
            String version = index.get("Version").getAsString();
            if (!ServerVersion.getCurrentVersion().name().equalsIgnoreCase(version))
                return;

            this.cachedNMSFile = nmsFile;
        } catch (Exception ignored) {
            // ignored
        }
    }

    public void loadNMSFile() {
        if (this.cachedNMSFile == null) {
            this.reobfNMS();
        }

        this.loadNMSFileFromCache();
    }

    private IMappingFile mappings() {
        if (this.mappingFile == null) {
            try {
                this.mappingFile = IMappingFile.load(Bukkit.class.getClassLoader().getResourceAsStream(
                        "META-INF/mappings/reobf.tiny")).reverse();
            } catch (Exception error) {
                Log.error(error, "An exception occurred while loading mappings");
            }
        }

        return this.mappingFile;
    }

    private void reobfNMS() {
        File nmsFileResource = new File(this.plugin.getDataFolder(), "nms_impl");
        File nmsCacheFolder = new File(this.plugin.getDataFolder(), ".nms");
        File nmsFile = new File(nmsCacheFolder, "nms.jar");

        nmsCacheFolder.mkdirs();

        if (nmsFileResource.exists()) nmsFileResource.delete();
        this.plugin.saveResource("nms_impl", false);

        File serverJar;

        try {
            Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
            serverJar = new File(minecraftServerClass.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        } catch (Exception error) {
            throw new RuntimeException(error);
        }

        Log.info(serverJar.getAbsolutePath());

        try (Renamer renamer = Renamer.builder()
                .add(Transformer.renamerFactory(this.mappings(), false))
                .add(Transformer.signatureStripperFactory(SignatureStripperConfig.ALL))
                .lib(serverJar)
                .threads(1)
                .logger(Log::info)
                .debug(Log::info)
                .build()) {
            renamer.run(nmsFileResource, nmsFile);
            nmsFileResource.delete();
        } catch (Exception error) {
            Log.error(error, "An unexpected error occurred while running Renamer");
        }

        this.cachedNMSFile = nmsFile;
    }

    private void loadNMSFileFromCache() {
        assert this.cachedNMSFile != null;
    }

}
