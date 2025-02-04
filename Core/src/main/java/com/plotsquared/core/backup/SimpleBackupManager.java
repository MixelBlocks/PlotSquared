/*
 *       _____  _       _    _____                                _
 *      |  __ \| |     | |  / ____|                              | |
 *      | |__) | | ___ | |_| (___   __ _ _   _  __ _ _ __ ___  __| |
 *      |  ___/| |/ _ \| __|\___ \ / _` | | | |/ _` | '__/ _ \/ _` |
 *      | |    | | (_) | |_ ____) | (_| | |_| | (_| | | |  __/ (_| |
 *      |_|    |_|\___/ \__|_____/ \__, |\__,_|\__,_|_|  \___|\__,_|
 *                                    | |
 *                                    |_|
 *            PlotSquared plot management system for Minecraft
 *               Copyright (C) 2014 - 2022 IntellectualSites
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.plotsquared.core.backup;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.plotsquared.core.PlotSquared;
import com.plotsquared.core.configuration.Settings;
import com.plotsquared.core.configuration.caption.Templates;
import com.plotsquared.core.configuration.caption.TranslatableCaption;
import com.plotsquared.core.inject.factory.PlayerBackupProfileFactory;
import com.plotsquared.core.player.PlotPlayer;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.util.task.TaskManager;
import net.kyori.adventure.text.minimessage.Template;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * {@inheritDoc}
 */
@Singleton
public class SimpleBackupManager implements BackupManager {

    private final Path backupPath;
    private final boolean automaticBackup;
    private final int backupLimit;
    private final Cache<PlotCacheKey, BackupProfile> backupProfileCache = CacheBuilder.newBuilder()
            .expireAfterAccess(3, TimeUnit.MINUTES).build();
    private final PlayerBackupProfileFactory playerBackupProfileFactory;

    @Inject
    public SimpleBackupManager(final @NonNull PlayerBackupProfileFactory playerBackupProfileFactory) throws Exception {
        this.playerBackupProfileFactory = playerBackupProfileFactory;
        this.backupPath = Objects.requireNonNull(PlotSquared.platform()).getDirectory().toPath().resolve("backups");
        if (!Files.exists(backupPath)) {
            Files.createDirectory(backupPath);
        }
        this.automaticBackup = Settings.Backup.AUTOMATIC_BACKUPS;
        this.backupLimit = Settings.Backup.BACKUP_LIMIT;
    }

    public SimpleBackupManager(
            final Path backupPath, final boolean automaticBackup,
            final int backupLimit, final PlayerBackupProfileFactory playerBackupProfileFactory
    ) {
        this.backupPath = backupPath;
        this.automaticBackup = automaticBackup;
        this.backupLimit = backupLimit;
        this.playerBackupProfileFactory = playerBackupProfileFactory;
    }

    @Override
    public @NonNull BackupProfile getProfile(final @NonNull Plot plot) {
        if (plot.hasOwner()) {
            try {
                return backupProfileCache.get(
                        new PlotCacheKey(plot),
                        () -> this.playerBackupProfileFactory.create(plot.getOwnerAbs(), plot)
                );
            } catch (ExecutionException e) {
                final BackupProfile profile = this.playerBackupProfileFactory.create(plot.getOwnerAbs(), plot);
                this.backupProfileCache.put(new PlotCacheKey(plot), profile);
                return profile;
            }
        }
        return new NullBackupProfile();
    }

    @Override
    public void automaticBackup(@Nullable PlotPlayer<?> player, final @NonNull Plot plot, @NonNull Runnable whenDone) {
        final BackupProfile profile;
        if (!this.shouldAutomaticallyBackup() || (profile = getProfile(plot)) instanceof NullBackupProfile) {
            whenDone.run();
        } else {
            if (player != null) {
                player.sendMessage(
                        TranslatableCaption.of("backups.backup_automatic_started"),
                        Template.of("plot", plot.getId().toString())
                );
            }
            profile.createBackup().whenComplete((backup, throwable) -> {
                if (throwable != null) {
                    if (player != null) {
                        player.sendMessage(
                                TranslatableCaption.of("backups.backup_automatic_failure"),
                                Templates.of("reason", throwable.getMessage())
                        );
                    }
                    throwable.printStackTrace();
                } else {
                    if (player != null) {
                        player.sendMessage(TranslatableCaption.of("backups.backup_automatic_finished"));
                        TaskManager.runTaskAsync(whenDone);
                    }
                }
            });
        }
    }

    @Override
    public boolean shouldAutomaticallyBackup() {
        return this.automaticBackup;
    }

    public Path getBackupPath() {
        return this.backupPath;
    }

    public int getBackupLimit() {
        return this.backupLimit;
    }

    private static final class PlotCacheKey {

        private final Plot plot;

        private PlotCacheKey(Plot plot) {
            this.plot = plot;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final PlotCacheKey that = (PlotCacheKey) o;
            return Objects.equals(plot.getArea(), that.plot.getArea())
                    && Objects.equals(plot.getId(), that.plot.getId())
                    && Objects.equals(plot.getOwnerAbs(), that.plot.getOwnerAbs());
        }

        @Override
        public int hashCode() {
            return Objects.hash(plot.getArea(), plot.getId(), plot.getOwnerAbs());
        }

    }

}
