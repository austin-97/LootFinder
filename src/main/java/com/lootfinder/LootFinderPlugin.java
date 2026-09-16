package com.lootfinder;

import com.google.inject.Provides;
import javax.inject.Inject;
import java.util.UUID;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuShouldLeftClick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsConfig;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;

@PluginDescriptor(name = "Loot Finder", description = "Filter loot in your bank from the Loot Tracker plugin", tags = {"bank", "loot", "tags"})
@PluginDependency(BankTagsPlugin.class)
public class LootFinderPlugin extends Plugin
{
	// One private, session-only name avoids merging with a user's saved tag.
	private final String tagName = "loot-finder:" + UUID.randomUUID();
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private LootFinderConfig config;
	@Inject private ConfigManager configManager;
	@Inject private LootTrackerRepository repository;
	@Inject private LootBankTag tag;
	@Inject private LootBankButton button;
	@Inject private TagManager tagManager;
	@Inject private BankTagsService bankTags;
	@Inject private BankTagsConfig bankTagsConfig;
	private boolean running;
	private boolean registered;
	private String selectedSourceName;

	@Provides
	LootFinderConfig provideConfig(ConfigManager manager)
	{
		LootFinderConfigMigration.migrate(manager::getConfiguration, manager::setConfiguration);
		return manager.getConfig(LootFinderConfig.class);
	}

	@Override
	protected void startUp()
	{
		button.loadImage();
		running = true;
		clientThread.invokeLater(() ->
		{
			if (!running) return;
			removeStaleLootTab();
			refresh();
		});
	}

	@Override
	protected void shutDown()
	{
		running = false;
		clientThread.invokeLater(() ->
		{
			clear();
			unregister();
			button.shutDown();
			repository.clearLive();
		});
	}

	private void refresh()
	{
		if (!running || client.getGameState() != GameState.LOGGED_IN) return;
		List<LootSource> all = repository.recent(Integer.MAX_VALUE);
		List<LootSource> recent = all.subList(0, Math.min(all.size(), Math.max(1, Math.min(20, config.recentSources()))));
		button.show(config.menuLocation(), recent, LootTrackerRepository.favorites(all, config.favoriteSources()), source ->
		{
			if (!running) return;
			tag.select(source);
			selectedSourceName = source.getName();
			if (!registered)
			{
			tagManager.registerTag(tagName, tag);
				registered = true;
			}
			openSelectedLootFilter();
		}, this::clear, this::canAddCurrentFavorite, this::addCurrentFavorite);
	}

	private boolean canAddCurrentFavorite()
	{
		if (!running || selectedSourceName == null || !tagName.equals(bankTags.getActiveTag())) return false;
		String favorites = config.favoriteSources();
		if (favorites != null)
		{
			for (String name : favorites.split(","))
			{
				if (name.trim().equalsIgnoreCase(selectedSourceName.trim())) return false;
			}
		}
		return true;
	}

	private void addCurrentFavorite()
	{
		if (!canAddCurrentFavorite()) return;
		String favorites = config.favoriteSources();
		String updated = favorites == null || favorites.trim().isEmpty()
			? selectedSourceName : favorites + ", " + selectedSourceName;
		configManager.setConfiguration(LootFinderConfig.GROUP, "favoriteNpcs", updated);
		// ConfigChanged refreshes the menu using the saved favorites.
	}

	private void openSelectedLootFilter()
	{
		if (!running || selectedSourceName == null || !registered) return;
		// Allow normal bank dragging while keeping the filter in the bank's actual order.
		bankTags.openBankTag(tagName, BankTagsService.OPTION_NO_LAYOUT
			| BankTagsService.OPTION_HIDE_TAG_NAME | BankTagsService.OPTION_ALLOW_MODIFICATIONS);
		// Keep the selection in memory without saving a session-only tag in Bank Tags.
		forgetLootTab();
	}

	private static boolean isLootTab(String name)
	{
		return name != null && name.matches("loot-finder:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
	}

	private void forgetLootTab()
	{
		if (isLootTab(bankTagsConfig.tab())) bankTagsConfig.tab("");
	}

	private void removeStaleLootTab()
	{
		// Recover UUID tags saved by earlier versions, including across client restarts.
		if (selectedSourceName == null && isLootTab(bankTags.getActiveTag()))
		{
			bankTags.closeBankTag();
		}
		forgetLootTab();
	}

	private void clear()
	{
		selectedSourceName = null;
		if (tagName.equals(bankTags.getActiveTag())) bankTags.closeBankTag();
		tag.clear();
	}

	private void unregister()
	{
		forgetLootTab();
		if (registered) tagManager.unregisterTag(tagName);
		registered = false;
		selectedSourceName = null;
		tag.clear();
	}

	// Follow Bank Tags' own pre-init restoration, after it initializes its tab UI.
	@Subscribe(priority = -1)
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_INIT
			&& running && registered && selectedSourceName != null)
		{
			// The server restores the vanilla tab on bank open; use all items for loot filtering.
			client.setVarbit(VarbitID.BANK_CURRENTTAB, 0);
			openSelectedLootFilter();
		}
	}

	// Set the label before BankPlugin's default-priority handler appends bank value.
	@Subscribe(priority = 1)
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_INIT)
		{
			Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
			// ScriptPostFired still runs inside the interpreter. Defer stale-tag cleanup,
			// which can run scripts, but do not rebuild the already restored filter.
			clientThread.invokeLater(() ->
			{
				if (!running || client.getGameState() != GameState.LOGGED_IN
					|| bank == null || bank.isHidden()
					|| client.getWidget(InterfaceID.Bankmain.UNIVERSE) != bank) return;
				removeStaleLootTab();
				refresh();
			});
		}
		else if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING
			&& running && selectedSourceName != null && tagName.equals(bankTags.getActiveTag()))
		{
			Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
			if (title != null)
			{
				title.setText("Loot: " + Text.escapeJagex(selectedSourceName));
			}
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN && event.isUnload())
		{
			// Bank Tags clears its active filter on close; retain ours for the next open.
			button.detach();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		clientThread.invokeLater(() ->
		{
			if (!running) return;
			clear();
			unregister();
			button.detach();
			refresh();
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			unregister();
			button.detach();
		}
		else if (event.getGameState() == GameState.HOPPING)
		{
			button.detach();
		}
	}

	@Subscribe
	public void onMenuShouldLeftClick(MenuShouldLeftClick event)
	{
		if (running) button.onMenuShouldLeftClick(event);
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!running) return;
		button.onMenuOpened(event);
		if (tagName.equals(bankTags.getActiveTag()))
		{
			// Bank Tags couples dragging with tag-edit actions. Loot membership is derived
			// from recorded drops, so removing a saved tag would have no effect here.
			for (MenuEntry entry : client.getMenu().getMenuEntries())
			{
				if (entry.getType() == MenuAction.RUNELITE
					&& entry.getParam1() == InterfaceID.Bankmain.ITEMS
					&& "Remove-tag".equals(entry.getOption()))
				{
					client.getMenu().removeMenuEntry(entry);
				}
			}
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		LootFinderConfigMigration.migrate(configManager::getConfiguration, configManager::setConfiguration);
		clientThread.invokeLater(() ->
		{
			repository.clearLive();
			refresh();
		});
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		String profile = configManager.getRSProfileKey();
		clientThread.invoke(() ->
		{
			if (!running || !java.util.Objects.equals(profile, configManager.getRSProfileKey())) return;
			repository.record(profile, event);
			// The next bank open reads these updates even when no bank UI exists yet.
			Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
			if (bank != null && !bank.isHidden()) refresh();
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if ("loottracker".equals(event.getGroup()) && event.getKey().startsWith("drops_"))
		{
			clientThread.invokeLater(() ->
			{
				if (!running || !java.util.Objects.equals(event.getProfile(), configManager.getRSProfileKey())) return;
				if (event.getNewValue() == null) repository.removeLive(event.getProfile(), event.getKey());
				Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
				if (bank != null && !bank.isHidden()) refresh();
			});
			return;
		}
		if (LootFinderConfig.GROUP.equals(event.getGroup())
			|| ("loottracker".equals(event.getGroup()) && "ignoredEvents".equals(event.getKey())))
		{
			clientThread.invokeLater(this::refresh);
		}
	}
}
