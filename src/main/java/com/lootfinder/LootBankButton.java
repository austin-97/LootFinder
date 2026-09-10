package com.lootfinder;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuShouldLeftClick;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

final class LootBankButton
{
	private static final String OPEN_MENU = "Open loot sources";
	private static final String ADD_FAVORITE = "Add to favorites";
	@Inject private Client client;
	private Widget parent;
	private Widget button;
	private BufferedImage image;
	private SpritePixels sprite;
	private int spriteId;
	private List<LootSource> recents = java.util.Collections.emptyList();
	private List<LootSource> favorites = java.util.Collections.emptyList();
	private Consumer<LootSource> selection;
	private BooleanSupplier canAddFavorite;

	void loadImage()
	{
		image = ImageUtil.loadImageResource(LootTrackerPlugin.class, "panel_icon.png");
	}

	void show(List<LootSource> sources, List<LootSource> favoriteSources, Consumer<LootSource> select,
		Runnable clear, BooleanSupplier canAddFavorite, Runnable addFavorite)
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		if (bank == null || bank.isHidden()) return;
		if (sprite == null)
		{
			sprite = ImageUtil.getImageSpritePixels(image, client);
			// Allocate a free custom sprite ID rather than replacing another plugin's sprite.
			spriteId = -10000;
			while (client.getSpriteOverrides().containsKey(spriteId)) spriteId--;
			client.getSpriteOverrides().put(spriteId, sprite);
		}
		if (parent != bank || button == null)
		{
			detach();
			parent = bank;
			button = bank.createChild(-1, WidgetType.GRAPHIC);
			button.setOriginalWidth(20);
			button.setOriginalHeight(20);
			button.setOriginalX(380);
			button.setOriginalY(7);
			button.setSpriteId(spriteId);
			button.setHasListener(true);
		}
		button.setHidden(false);
		recents = List.copyOf(sources);
		favorites = List.copyOf(favoriteSources);
		selection = select;
		this.canAddFavorite = canAddFavorite;
		button.setName("Loot Finder");
		button.clearActions();
		button.setAction(0, OPEN_MENU);
		button.setAction(1, "Recents");
		button.setAction(2, "Favorites");
		button.setAction(3, "Clear loot filter");
		button.setAction(4, ADD_FAVORITE);
		button.setOnOpListener((JavaScriptCallback) event ->
		{
			if (event.getOp() == 4) clear.run();
			else if (event.getOp() == 5 && canAddFavorite.getAsBoolean()) addFavorite.run();
		});
		button.revalidate();
	}

	void onMenuShouldLeftClick(MenuShouldLeftClick event)
	{
		if (button == null || button.isHidden()) return;
		MenuEntry[] entries = client.getMenu().getMenuEntries();
		if (entries.length > 0 && entries[entries.length - 1].getWidget() == button)
		{
			event.setForceRightClick(true);
		}
	}

	void onMenuOpened(MenuOpened event)
	{
		if (button == null || button.isHidden()) return;
		List<MenuEntry> visibleEntries = new ArrayList<>();
		boolean removedOpener = false;
		for (MenuEntry entry : event.getMenuEntries())
		{
			// Keep the opener for hover text, but omit it once the menu is open.
			if (entry.getWidget() == button && (OPEN_MENU.equals(entry.getOption())
				|| (ADD_FAVORITE.equals(entry.getOption())
					&& (canAddFavorite == null || !canAddFavorite.getAsBoolean()))))
			{
				removedOpener = true;
				continue;
			}
			visibleEntries.add(entry);
			if (entry.getWidget() != button) continue;
			if ("Favorites".equals(entry.getOption())) populate(entry, favorites, "No matching favorites — configure source names");
			else if ("Recents".equals(entry.getOption())) populate(entry, recents, "No recorded loot");
		}
		if (removedOpener)
		{
			client.getMenu().setMenuEntries(visibleEntries.toArray(new MenuEntry[0]));
		}
	}

	private void populate(MenuEntry entry, List<LootSource> sources, String emptyMessage)
	{
		String section = entry.getOption();
		entry.setOption("Loot Finder:");
		entry.setTarget("<col=ff981f>" + section + "</col>");
		entry.setType(MenuAction.RUNELITE);
		Menu submenu = entry.createSubMenu();
		if (sources.isEmpty())
		{
			submenu.createMenuEntry(0).setOption(emptyMessage).setType(MenuAction.RUNELITE);
			return;
		}
		for (LootSource source : sources)
		{
			boolean duplicateName = sources.stream().filter(other -> other.getName().equals(source.getName())).count() > 1;
			String label = source.getName() + (duplicateName ? " (" + source.getType() + ")" : "");
			submenu.createMenuEntry(0)
				.setOption("Show loot")
				.setTarget("<col=ff981f>" + Text.escapeJagex(label) + "</col>")
				.setType(MenuAction.RUNELITE)
				.onClick(clicked ->
				{
					if (button != null && !button.isHidden() && selection != null) selection.accept(source);
				});

		}
	}

	void detach()
	{
		if (button != null)
		{
			button.clearActions();
			button.setOnOpListener((Object[]) null);
			button.setHidden(true);
		}
		button = null;
		parent = null;
		recents = java.util.Collections.emptyList();
		favorites = java.util.Collections.emptyList();
		selection = null;
		canAddFavorite = null;
	}

	void shutDown()
	{
		detach();
		if (sprite != null)
		{
			client.getSpriteOverrides().remove(spriteId, sprite);
			sprite = null;
		}
		image = null;
	}
}

