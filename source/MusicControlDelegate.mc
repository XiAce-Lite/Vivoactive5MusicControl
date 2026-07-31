import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

class MusicControlDelegate extends WatchUi.BehaviorDelegate {

    private var _view as MusicControlView;

    function initialize(view as MusicControlView) {
        BehaviorDelegate.initialize();
        _view = view;
    }

    function onTap(clickEvent as ClickEvent) as Boolean {
        var coords = clickEvent.getCoordinates();
        var hit = _view.hitTest(coords[0], coords[1]);
        if (hit == :prev) {
            _view.onPrev();
            PhoneBridge.sendCommand("prev");
            System.println("prev");
            return true;
        }
        if (hit == :play) {
            _view.togglePlay();
            PhoneBridge.sendCommand("toggle");
            System.println(_view.isPlaying() ? "play" : "pause");
            return true;
        }
        if (hit == :next) {
            _view.onNext();
            PhoneBridge.sendCommand("next");
            System.println("next");
            return true;
        }
        if (hit == :volume) {
            var volView = new VolumeView();
            WatchUi.pushView(volView, new VolumeDelegate(volView), WatchUi.SLIDE_LEFT);
            return true;
        }
        return false;
    }

    function onBack() as Boolean {
        return false;
    }
}
