package machindustry;

import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Unit;
import mindustry.input.InputHandler;
import mindustry.mod.Mod;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;
import mindustry.world.blocks.defense.OverdriveProjector;
import mindustry.world.blocks.power.BeamNode;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.meta.BlockGroup;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Liquids;
import mindustry.content.StatusEffects;
import mindustry.entities.Units;
import mindustry.entities.units.BuildPlan;

import java.lang.reflect.Field;
import java.util.LinkedList;

import arc.Core;
import arc.Events;
import arc.input.InputProcessor;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.util.Tmp;

public class Machindustry extends Mod {
    private Tile tile1 = null;
    private Tile tile2 = null;
    private boolean build = false;

    public Machindustry() {
        System.out.println("Hello");
        Core.input.addProcessor(new InputProcessor() {
            public boolean keyDown(KeyCode keycode) {
                if (keycode == KeyCode.l) {
                    build = !build;
                    return true;
                }
                return false;
            }
        });
        Events.on(EventType.TapEvent.class, event -> {
            if (build)
            {
            if (tile1 == null)
                tile1 = event.tile;
            else
            {
                Unit unit = Vars.player.unit();

				WorldState validPlace = null;
                try {
                    validPlace = new WorldState(Vars.world.height(), Vars.world.width());
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                SolidPathFinder pathFinder = new SolidPathFinder(Vars.world.height(), Vars.world.width());
				validPlace.UpdateMap();
                pathFinder.UpdateMap(validPlace.Map);
                pathFinder.UpdateMap(validPlace.BuildPlans);
                LinkedList<BuildPlan> buildPath = pathFinder.BuildPath(tile1, event.tile, false);
                tile1 = null;
                tile2 = null;

                if (buildPath != null)
                    for (BuildPlan buildPlan : buildPath)
                        unit.addBuild(buildPlan);
            }
            }

            // System.out.println(event.tile.build);
            // if (event.tile.build != null)
            // {
            //     System.out.println(event.tile.build.tile == event.tile);
            //     System.out.println(event.tile.block().offset);
            //     System.out.println(event.tile.block().sizeOffset);
            // }
            //createRandomBeamNode();
        });
    }
}