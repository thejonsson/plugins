/*
 * Copyright (c) 2017, Robin <robin.weymans@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.implings;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDefinitionChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.ui.overlay.OverlayManager;
import org.pf4j.Extension;

@Extension
@PluginDescriptor(
	name = "Implings",
	description = "Highlight nearby implings on the minimap and on-screen",
	tags = {"hunter", "minimap", "overlay", "imp"},
	type = PluginType.SKILLING
)
public class ImplingsPlugin extends Plugin
{
	private static final int DYNAMIC_SPAWN_NATURE_DRAGON = 1618;
	private static final int DYNAMIC_SPAWN_ECLECTIC = 1633;
	private static final int DYNAMIC_SPAWN_BABY_ESSENCE = 1634;

	@Getter(AccessLevel.PACKAGE)
	private Map<ImplingType, Integer> implingCounterMap = new HashMap<>();

	@Getter(AccessLevel.PACKAGE)
	private final List<NPC> implings = new ArrayList<>();

	@Getter(AccessLevel.PACKAGE)
	private Map<Integer, String> dynamicSpawns = new HashMap<>();

	@Inject
	private ImplingsOverlay overlay;

	@Inject
	private ImplingCounterOverlay implingCounterOverlay;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ImplingMinimapOverlay minimapOverlay;

	@Inject
	private ImplingsConfig config;

	@Inject
	private Notifier notifier;

	@Provides
	ImplingsConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ImplingsConfig.class);
	}

	@Override
	protected void startUp()
	{
		dynamicSpawns.put(DYNAMIC_SPAWN_NATURE_DRAGON, "T3 Nature-Lucky Dynamic");
		dynamicSpawns.put(DYNAMIC_SPAWN_ECLECTIC, "T2 Eclectic Dynamic");
		dynamicSpawns.put(DYNAMIC_SPAWN_BABY_ESSENCE, "T1 Baby-Essence Dynamic");

		overlayManager.add(overlay);
		overlayManager.add(minimapOverlay);
		overlayManager.add(implingCounterOverlay);
	}

	@Override
	protected void shutDown()
	{
		implings.clear();
		overlayManager.remove(overlay);
		overlayManager.remove(minimapOverlay);
		overlayManager.remove(implingCounterOverlay);
	}

	@Subscribe
	private void onGameTick(GameTick event)
	{
		implingCounterMap.clear();
		for (NPC npc : implings)
		{
			Impling impling = Impling.findImpling(npc.getId());

			if (impling == null || impling.getImplingType() == null)
			{
				continue;
			}

			ImplingType type = impling.getImplingType();
			if (implingCounterMap.containsKey(type))
			{
				implingCounterMap.put(type, implingCounterMap.get(type) + 1);
			}
			else
			{
				implingCounterMap.put(type, 1);
			}
		}
	}

	@Subscribe
	private void onNpcSpawned(NpcSpawned npcSpawned)
	{
		NPC npc = npcSpawned.getNpc();
		Impling impling = Impling.findImpling(npc.getId());

		if (impling != null)
		{
			if (showImplingType(impling.getImplingType()) == ImplingsConfig.ImplingMode.NOTIFY)
			{
				notifier.notify(impling.getImplingType().getName() + " impling is in the area");
			}

			implings.add(npc);
		}
	}

	@Subscribe
	private void onNpcDefinitionChanged(NpcDefinitionChanged npcCompositionChanged)
	{
		NPC npc = npcCompositionChanged.getNpc();
		Impling impling = Impling.findImpling(npc.getId());

		if (impling != null)
		{
			if (showImplingType(impling.getImplingType()) == ImplingsConfig.ImplingMode.NOTIFY)
			{
				notifier.notify(impling.getImplingType().getName() + " impling is in the area");
			}

			if (!implings.contains(npc))
			{
				implings.add(npc);
			}
		}
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			implings.clear();
			implingCounterMap.clear();
		}
	}

	@Subscribe
	private void onNpcDespawned(NpcDespawned npcDespawned)
	{
		if (implings.isEmpty())
		{
			return;
		}

		NPC npc = npcDespawned.getNpc();
		implings.remove(npc);

	}

	boolean showNpc(NPC npc)
	{
		Impling impling = Impling.findImpling(npc.getId());
		if (impling == null)
		{
			return true;
		}

		ImplingsConfig.ImplingMode impMode = showImplingType(impling.getImplingType());
		return impMode == ImplingsConfig.ImplingMode.HIGHLIGHT || impMode == ImplingsConfig.ImplingMode.NOTIFY;
	}

	ImplingsConfig.ImplingMode showImplingType(ImplingType implingType)
	{
		switch (implingType)
		{
			case BABY:
				return config.showBaby();
			case YOUNG:
				return config.showYoung();
			case GOURMET:
				return config.showGourmet();
			case EARTH:
				return config.showEarth();
			case ESSENCE:
				return config.showEssence();
			case ECLECTIC:
				return config.showEclectic();
			case NATURE:
				return config.showNature();
			case MAGPIE:
				return config.showMagpie();
			case NINJA:
				return config.showNinja();
			case CRYSTAL:
				return config.showCrystal();
			case DRAGON:
				return config.showDragon();
			case LUCKY:
				return config.showLucky();
			default:
				return ImplingsConfig.ImplingMode.NONE;
		}
	}

	Color npcToColor(NPC npc)
	{
		Impling impling = Impling.findImpling(npc.getId());
		if (impling == null)
		{
			return null;
		}

		return typeToColor(impling.getImplingType());
	}

	private Color typeToColor(ImplingType type)
	{
		switch (type)
		{

			case BABY:
				return config.getBabyColor();
			case YOUNG:
				return config.getYoungColor();
			case GOURMET:
				return config.getGourmetColor();
			case EARTH:
				return config.getEarthColor();
			case ESSENCE:
				return config.getEssenceColor();
			case ECLECTIC:
				return config.getEclecticColor();
			case NATURE:
				return config.getNatureColor();
			case MAGPIE:
				return config.getMagpieColor();
			case NINJA:
				return config.getNinjaColor();
			case CRYSTAL:
				return config.getCrystalColor();

			case DRAGON:
				return config.getDragonColor();
			case LUCKY:
				return config.getLuckyColor();
			default:
				return null;
		}
	}
}
