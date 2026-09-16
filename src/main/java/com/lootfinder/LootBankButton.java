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
	private LootMenuLocation location;
	private Runnable clearFilter;
	private Runnable addCurrentFavorite;

	void loadImage()
	{
		image = ImageUtil.loadImageResource(LootTrackerPlugin.class, "panel_icon.png");
	}

	void show(LootMenuLocation location, List<LootSource> sources, List<LootSource> favoriteSources, Consumer<LootSource> select,
		Runnable clear, BooleanSupplier canAddFavorite, Runnable addFavorite)
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		if (bank == null || bank.isHidden()) return;
		if (parent != bank) detach();
		parent = bank;
		this.location = location;
		recents = List.copyOf(sources);
		favorites = List.copyOf(favoriteSources);
		selection = select;
		this.canAddFavorite = canAddFavorite;
		clearFilter = clear;
		addCurrentFavorite = addFavorite;
		if (location != LootMenuLocation.SEPARATE_ICON)
		{
			if (button != null)
			{
				button.clearActions();
				button.setOnOpListener((Object[]) null);
				button.setHidden(true);
			}
			return;
		}
		if (sprite == null)
		{
			sprite = ImageUtil.getImageSpritePixels(image, client);
			// Allocate a free custom sprite ID rather than replacing another plugin's sprite.
			spriteId = -10000;
			while (client.getSpriteOverrides().containsKey(spriteId)) spriteId--;
			client.getSpriteOverrides().put(spriteId, sprite);
		}
		if (button == null)
		{
			button = bank.createChild(-1, WidgetType.GRAPHIC);
			button.setOriginalWidth(20);
			button.setOriginalHeight(20);
			button.setOriginalX(380);
			button.setOriginalY(7);
			button.setSpriteId(spriteId);
			button.setHasListener(true);
		}
		button.setHidden(false);
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
		if (location != LootMenuLocation.SEPARATE_ICON || button == null || button.isHidden()) return;
		MenuEntry[] entries = client.getMenu().getMenuEntries();
		if (entries.length > 0 && entries[entries.length - 1].getWidget() == button)
		{
			event.setForceRightClick(true);
		}
	}

	void onMenuOpened(MenuOpened event)
	{
		if (!isBankOpen()) return;
		if (location == LootMenuLocation.BANK_SETTINGS || location == LootMenuLocation.BANK_HELP)
		{
			Widget menuButton = client.getWidget(location == LootMenuLocation.BANK_HELP
				? InterfaceID.Bankmain.BANK_TUT : InterfaceID.Bankmain.MENU_BUTTON);
			if (menuButton == null || menuButton.isHidden()) return;
			for (MenuEntry entry : event.getMenuEntries())
			{
				if (entry.getWidget() == menuButton)
				{
					addBankButtonMenus();
					break;
				}
			}
			return;
		}
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

	private boolean isBankOpen()
	{
		return parent != null && !parent.isHidden()
			&& client.getWidget(InterfaceID.Bankmain.UNIVERSE) == parent;
	}

	private void addBankButtonMenus()
	{
		Menu menu = client.getMenu();
		// Only add client-side menu entries; leave the bank widget and its actions intact.
		// The last entry is displayed first, so add our actions in reverse display order.
		if (canAddFavorite != null && canAddFavorite.getAsBoolean())
		{
			menu.createMenuEntry(-1).setOption(ADD_FAVORITE).setTarget("Loot Finder")
				.setType(MenuAction.RUNELITE).onClick(entry ->
				{
					if (isBankOpen() && canAddFavorite != null && canAddFavorite.getAsBoolean()
						&& addCurrentFavorite != null) addCurrentFavorite.run();
				});
		}
		menu.createMenuEntry(-1).setOption("Clear loot filter").setTarget("Loot Finder")
			.setType(MenuAction.RUNELITE).onClick(entry ->
			{
				if (isBankOpen() && clearFilter != null) clearFilter.run();
			});
		populate(menu.createMenuEntry(-1).setOption("Favorites"), favorites, "No matching favorites — configure source names");
		populate(menu.createMenuEntry(-1).setOption("Recents"), recents, "No recorded loot");
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
					if (isBankOpen() && selection != null) selection.accept(source);
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
		clearFilter = null;
		addCurrentFavorite = null;
		location = null;
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

