package au.com.huntmap.morisset.car;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

import au.com.huntmap.morisset.CarLog;

/** Entry point for the Android Auto (projected) experience. */
public class HuntMapCarAppService extends CarAppService {

    @Override
    public void onCreate() {
        super.onCreate();
        CarLog.log(this, "CarAppService created (Android Auto bound to the app)");
    }

    @NonNull
    @Override
    public HostValidator createHostValidator() {
        CarLog.log(this, "host validator requested");
        // Side-loaded personal app: accept any host (Android Auto itself).
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull
    @Override
    public Session onCreateSession() {
        CarLog.log(this, "session created (head unit opened Hunt Map)");
        return new Session() {
            @NonNull
            @Override
            public Screen onCreateScreen(@NonNull Intent intent) {
                CarLog.log(getCarContext(), "screen requested");
                return new MapScreen(getCarContext());
            }
        };
    }
}
