package hawkshock.sleepnotifier.client;

import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import hawkshock.sleepnotifier.client.ui.SleepNotifierConfigScreen;

public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SleepNotifierConfigScreen::new; // ModMenu passes the parent screen
	}
}