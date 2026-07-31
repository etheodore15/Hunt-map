package au.com.huntmap.morisset;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(TileStorePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
