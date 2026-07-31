import Toybox.Lang;
import Toybox.WatchUi;

class VolumeDelegate extends WatchUi.BehaviorDelegate {

    private var _view as VolumeView;

    function initialize(view as VolumeView) {
        BehaviorDelegate.initialize();
        _view = view;
    }

    function onTap(clickEvent as ClickEvent) as Boolean {
        var coords = clickEvent.getCoordinates();
        var hit = _view.hitTest(coords[0], coords[1]);
        if (hit == :minus) {
            _view.adjust(-5);
            return true;
        }
        if (hit == :plus) {
            _view.adjust(5);
            return true;
        }
        return false;
    }

    function onBack() as Boolean {
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        return true;
    }

    function onSwipe(swipeEvent as SwipeEvent) as Boolean {
        if (swipeEvent.getDirection() == WatchUi.SWIPE_RIGHT) {
            WatchUi.popView(WatchUi.SLIDE_RIGHT);
            return true;
        }
        return false;
    }
}
