package com.buildingassistant.client.command;

import com.buildingassistant.client.ProjectActions;
import com.buildingassistant.config.ClientConfig;
import com.buildingassistant.config.GhostStyle;
import com.buildingassistant.library.SchematicLibrary;
import com.buildingassistant.schematic.Schematic;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

import java.util.Locale;
import java.util.Optional;

/**
 * The client-side {@code /ba} command. It runs entirely on the client, needs no permissions and offers
 * the same actions as the hotkeys, which also makes every feature scriptable and easy to test.
 */
public final class BuildingAssistantCommand {

    private static final int MAX_MOVE = 256;

    private BuildingAssistantCommand() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ba");

        root.then(Commands.literal("list").executes(BuildingAssistantCommand::list));
        root.then(Commands.literal("start")
                .then(Commands.argument("schematic", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                SchematicLibrary.all().stream().map(Schematic::id), builder))
                        .executes(BuildingAssistantCommand::start)));
        root.then(Commands.literal("stop").executes(context -> run(ProjectActions::stop)));
        root.then(Commands.literal("here").executes(context -> run(ProjectActions::moveToCrosshair)));
        root.then(Commands.literal("move")
                .then(Commands.argument("dx", IntegerArgumentType.integer(-MAX_MOVE, MAX_MOVE))
                        .then(Commands.argument("dy", IntegerArgumentType.integer(-MAX_MOVE, MAX_MOVE))
                                .then(Commands.argument("dz", IntegerArgumentType.integer(-MAX_MOVE, MAX_MOVE))
                                        .executes(context -> run(() -> ProjectActions.moveBy(
                                                IntegerArgumentType.getInteger(context, "dx"),
                                                IntegerArgumentType.getInteger(context, "dy"),
                                                IntegerArgumentType.getInteger(context, "dz"))))))));
        root.then(Commands.literal("rotate").executes(context -> run(ProjectActions::rotate)));
        root.then(Commands.literal("mirror").executes(context -> run(ProjectActions::mirror)));
        root.then(Commands.literal("show").executes(context -> run(() -> ProjectActions.setGhostsVisible(true))));
        root.then(Commands.literal("hide").executes(context -> run(() -> ProjectActions.setGhostsVisible(false))));
        root.then(Commands.literal("opacity")
                .then(Commands.argument("percent", IntegerArgumentType.integer(15, 100))
                        .executes(BuildingAssistantCommand::opacity)));

        LiteralArgumentBuilder<CommandSourceStack> style = Commands.literal("style");
        for (GhostStyle value : GhostStyle.values()) {
            style.then(Commands.literal(value.name().toLowerCase(Locale.ROOT)).executes(context -> setStyle(context, value)));
        }
        root.then(style);

        dispatcher.register(root);
    }

    private static int run(Runnable action) {
        action.run();
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        if (!SchematicLibrary.isLoaded()) {
            context.getSource().sendFailure(Component.translatable("buildingassistant.message.library_loading"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                "buildingassistant.message.library_count", SchematicLibrary.all().size()), false);
        for (Schematic schematic : SchematicLibrary.all()) {
            context.getSource().sendSuccess(() -> Component.translatable("buildingassistant.message.library_entry",
                    schematic.id(), ProjectActions.displayName(schematic),
                    schematic.sizeX(), schematic.sizeY(), schematic.sizeZ(), schematic.requiredBlockCount()), false);
        }
        return SchematicLibrary.all().size();
    }

    private static int start(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "schematic");
        Optional<Schematic> schematic = SchematicLibrary.get(id);
        if (schematic.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("buildingassistant.message.unknown_schematic", id));
            return 0;
        }
        ProjectActions.start(schematic.get());
        return 1;
    }

    private static int opacity(CommandContext<CommandSourceStack> context) {
        int percent = IntegerArgumentType.getInteger(context, "percent");
        ClientConfig.GHOST_OPACITY.set(percent / 100.0);
        ClientConfig.GHOST_OPACITY.save();
        context.getSource().sendSuccess(
                () -> Component.translatable("buildingassistant.message.opacity", percent), false);
        return 1;
    }

    private static int setStyle(CommandContext<CommandSourceStack> context, GhostStyle style) {
        ClientConfig.GHOST_STYLE.set(style);
        ClientConfig.GHOST_STYLE.save();
        context.getSource().sendSuccess(() -> Component.translatable("buildingassistant.message.style",
                Component.translatable("buildingassistant.ghost_style." + style.name().toLowerCase(Locale.ROOT))), false);
        return 1;
    }
}
