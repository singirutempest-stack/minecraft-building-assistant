package com.buildingassistant.client.command;

import com.buildingassistant.client.ProjectActions;
import com.buildingassistant.config.ClientConfig;
import com.buildingassistant.config.GhostStyle;
import com.buildingassistant.library.SchematicLibrary;
import com.buildingassistant.project.BuildProject;
import com.buildingassistant.project.BuildView;
import com.buildingassistant.project.ProjectManager;
import com.buildingassistant.schematic.Schematic;
import com.buildingassistant.schematic.format.SchematicFormat;
import com.buildingassistant.validation.ValidationMode;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
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

        LiteralArgumentBuilder<CommandSourceStack> layer = Commands.literal("layer");
        layer.then(Commands.argument("number", IntegerArgumentType.integer(1, SchematicFormat.MAX_SIZE))
                .executes(context -> run(() -> ProjectActions.selectLayer(IntegerArgumentType.getInteger(context, "number")))));
        layer.then(Commands.literal("up").executes(context -> run(() -> ProjectActions.changeLayer(1))));
        layer.then(Commands.literal("down").executes(context -> run(() -> ProjectActions.changeLayer(-1))));
        layer.then(Commands.literal("all").executes(context -> run(() -> ProjectActions.setLayerMode(BuildView.LayerMode.ALL))));
        layer.then(Commands.literal("single").executes(context -> run(() -> ProjectActions.setLayerMode(BuildView.LayerMode.SINGLE))));
        layer.then(Commands.literal("near").executes(context -> run(() -> ProjectActions.setLayerMode(BuildView.LayerMode.NEIGHBOURS))));
        layer.then(Commands.literal("upto").executes(context -> run(() -> ProjectActions.setLayerMode(BuildView.LayerMode.UP_TO))));
        for (String part : new String[]{"foundation", "walls", "roof"}) {
            layer.then(Commands.literal(part).executes(context -> run(() -> ProjectActions.jumpToPart(part))));
        }
        root.then(layer);

        root.then(Commands.literal("floor")
                .then(Commands.literal("up").executes(context -> run(() -> ProjectActions.changeFloor(1))))
                .then(Commands.literal("down").executes(context -> run(() -> ProjectActions.changeFloor(-1)))));

        LiteralArgumentBuilder<CommandSourceStack> stage = Commands.literal("stage");
        stage.then(Commands.argument("number", IntegerArgumentType.integer(1, SchematicFormat.MAX_STAGES))
                .executes(context -> run(() -> ProjectActions.selectStage(IntegerArgumentType.getInteger(context, "number")))));
        stage.then(Commands.literal("next").executes(context -> run(() -> ProjectActions.changeStage(1))));
        stage.then(Commands.literal("prev").executes(context -> run(() -> ProjectActions.changeStage(-1))));
        stage.then(Commands.literal("all").executes(context -> run(() -> ProjectActions.setStageMode(BuildView.StageMode.ALL))));
        stage.then(Commands.literal("current").executes(context -> run(() -> ProjectActions.setStageMode(BuildView.StageMode.CURRENT))));
        stage.then(Commands.literal("upto").executes(context -> run(() -> ProjectActions.setStageMode(BuildView.StageMode.UP_TO))));
        root.then(stage);

        root.then(Commands.literal("pause").executes(context -> run(() -> ProjectActions.setPaused(true))));
        root.then(Commands.literal("resume").executes(context -> run(() -> ProjectActions.setPaused(false))));

        root.then(Commands.literal("project")
                .then(Commands.literal("list").executes(BuildingAssistantCommand::listProjects))
                .then(Commands.literal("next").executes(context -> run(ProjectActions::selectNextProject)))
                .then(Commands.literal("select")
                        .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                .executes(BuildingAssistantCommand::selectProject))));

        root.then(Commands.literal("next").executes(context -> run(ProjectActions::nextError)));
        root.then(Commands.literal("status").executes(BuildingAssistantCommand::status));

        LiteralArgumentBuilder<CommandSourceStack> mode = Commands.literal("mode");
        for (ValidationMode value : ValidationMode.values()) {
            mode.then(Commands.literal(value.name().toLowerCase(Locale.ROOT))
                    .executes(context -> run(() -> ProjectActions.setValidationMode(value))));
        }
        root.then(mode);

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

    private static int listProjects(CommandContext<CommandSourceStack> context) {
        ProjectManager manager = ProjectManager.get();
        if (manager.projects().isEmpty()) {
            context.getSource().sendFailure(Component.translatable("buildingassistant.message.no_project"));
            return 0;
        }
        int number = 1;
        for (BuildProject project : manager.projects()) {
            BlockPos anchor = project.placement().anchor();
            int required = project.schematic().requiredBlockCount();
            int placed = project.validation() != null ? project.validation().correct() : project.lastKnownPlaced();
            String key = project == manager.current()
                    ? "buildingassistant.message.project_entry_current"
                    : project.paused() ? "buildingassistant.message.project_entry_paused"
                    : "buildingassistant.message.project_entry";
            int index = number++;
            context.getSource().sendSuccess(() -> Component.translatable(key, index,
                    ProjectActions.displayName(project.schematic()), anchor.getX(), anchor.getY(), anchor.getZ(),
                    required == 0 ? 100 : Math.round(placed * 100.0F / required)), false);
        }
        return manager.projects().size();
    }

    private static int selectProject(CommandContext<CommandSourceStack> context) {
        int number = IntegerArgumentType.getInteger(context, "number");
        if (!ProjectActions.selectProject(number)) {
            context.getSource().sendFailure(Component.translatable("buildingassistant.message.no_such_project", number));
            return 0;
        }
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        Component summary = ProjectActions.statusSummary();
        if (summary == null) {
            context.getSource().sendFailure(Component.translatable("buildingassistant.message.no_project"));
            return 0;
        }
        context.getSource().sendSuccess(() -> summary, false);
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
