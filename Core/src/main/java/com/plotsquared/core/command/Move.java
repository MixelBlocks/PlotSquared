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
package com.plotsquared.core.command;

import com.google.inject.Inject;
import com.plotsquared.core.configuration.caption.TranslatableCaption;
import com.plotsquared.core.location.Location;
import com.plotsquared.core.permissions.Permission;
import com.plotsquared.core.player.PlotPlayer;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.PlotArea;
import com.plotsquared.core.plot.world.PlotAreaManager;
import com.plotsquared.core.util.Permissions;
import com.plotsquared.core.util.task.RunnableVal2;
import com.plotsquared.core.util.task.RunnableVal3;
import net.kyori.adventure.text.minimessage.Template;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.concurrent.CompletableFuture;

@CommandDeclaration(usage = "/plot move <X;Z>",
        command = "move",
        permission = "plots.move",
        category = CommandCategory.CLAIMING,
        requiredType = RequiredType.PLAYER)
public class Move extends SubCommand {

    private final PlotAreaManager plotAreaManager;

    @Inject
    public Move(final @NonNull PlotAreaManager plotAreaManager) {
        this.plotAreaManager = plotAreaManager;
    }

    @Override
    public CompletableFuture<Boolean> execute(
            PlotPlayer<?> player, String[] args,
            RunnableVal3<Command, Runnable, Runnable> confirm,
            RunnableVal2<Command, CommandResult> whenDone
    ) {
        Location location = player.getLocation();
        Plot plot1 = location.getPlotAbs();
        if (plot1 == null) {
            player.sendMessage(TranslatableCaption.of("errors.not_in_plot"));
            return CompletableFuture.completedFuture(false);
        }
        if (!plot1.isOwner(player.getUUID()) && !Permissions
                .hasPermission(player, Permission.PERMISSION_ADMIN)) {
            player.sendMessage(TranslatableCaption.of("permission.no_plot_perms"));
            return CompletableFuture.completedFuture(false);
        }
        boolean override = false;
        if (args.length == 2 && args[1].equalsIgnoreCase("-f")) {
            args = new String[]{args[0]};
            override = true;
        }
        if (args.length != 1) {
            sendUsage(player);
            return CompletableFuture.completedFuture(false);
        }
        PlotArea area = this.plotAreaManager.getPlotAreaByString(args[0]);
        Plot plot2;
        if (area == null) {
            plot2 = Plot.getPlotFromString(player, args[0], true);
            if (plot2 == null) {
                return CompletableFuture.completedFuture(false);
            }
        } else {
            plot2 = area.getPlotAbs(plot1.getId());
        }
        if (plot1.equals(plot2)) {
            player.sendMessage(TranslatableCaption.of("invalid.origin_cant_be_target"));
            return CompletableFuture.completedFuture(false);
        }
        if (!plot1.getArea().isCompatible(plot2.getArea()) && (!override || !Permissions
                .hasPermission(player, Permission.PERMISSION_ADMIN))) {
            player.sendMessage(TranslatableCaption.of("errors.plotworld_incompatible"));
            return CompletableFuture.completedFuture(false);
        }
        if (plot1.isMerged() || plot2.isMerged()) {
            player.sendMessage(TranslatableCaption.of("move.move_merged"));
            return CompletableFuture.completedFuture(false);
        }

        // Set strings here as the plot objects are mutable (the PlotID changes after being moved).
        String p1 = plot1.toString();
        String p2 = plot2.toString();

        return plot1.getPlotModificationManager().move(plot2, player, () -> {
        }, false).thenApply(result -> {
            if (result) {
                player.sendMessage(
                        TranslatableCaption.of("move.move_success"),
                        Template.of("origin", p1),
                        Template.of("target", p2)
                );
                return true;
            } else {
                player.sendMessage(TranslatableCaption.of("move.requires_unowned"));
                return false;
            }
        });
    }

    @Override
    public boolean onCommand(final PlotPlayer<?> player, String[] args) {
        return true;
    }

}
