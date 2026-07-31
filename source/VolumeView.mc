import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

//! Volume screen — large hit targets for +/- .
class VolumeView extends WatchUi.View {

    var minusRect as Array<Number> = [0, 0, 0, 0] as Array<Number>;
    var plusRect as Array<Number> = [0, 0, 0, 0] as Array<Number>;

    function initialize() {
        View.initialize();
    }

    function onUpdate(dc as Dc) as Void {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var cx = w / 2;
        var volume = getApp().getVolume();

        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        var titleY = (h * 0.10).toNumber();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, titleY, Graphics.FONT_SMALL, WatchUi.loadResource(Rez.Strings.VolumeTitle) as String, Graphics.TEXT_JUSTIFY_CENTER);

        var numFont = Graphics.FONT_NUMBER_MILD;
        var numY = titleY + dc.getFontHeight(Graphics.FONT_SMALL) + 8;
        dc.setColor(Graphics.COLOR_BLUE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, numY, numFont, volume.format("%d"), Graphics.TEXT_JUSTIFY_CENTER);

        // Large +/- buttons
        var btnW = (w * 0.32).toNumber();
        var btnH = (h * 0.20).toNumber();
        var btnY = (h * 0.62).toNumber();
        var gap = (w * 0.08).toNumber();
        var minusX = cx - btnW - gap / 2;
        var plusX = cx + gap / 2;
        minusRect = [minusX, btnY, minusX + btnW, btnY + btnH] as Array<Number>;
        plusRect = [plusX, btnY, plusX + btnW, btnY + btnH] as Array<Number>;

        var barW = (w * 0.62).toNumber();
        var barH = 12;
        var barX = cx - barW / 2;
        var numBottom = numY + dc.getFontHeight(numFont);
        var barY = numBottom + 14;
        if (barY + barH + 10 > btnY) {
            barY = btnY - barH - 12;
        }

        dc.setColor(Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.fillRoundedRectangle(barX, barY, barW, barH, 4);
        var fillW = (barW * volume / 100).toNumber();
        if (fillW > 0) {
            dc.setColor(Graphics.COLOR_BLUE, Graphics.COLOR_TRANSPARENT);
            dc.fillRoundedRectangle(barX, barY, fillW, barH, 4);
        }

        drawButton(dc, minusRect, "-");
        drawButton(dc, plusRect, "+");

        dc.setColor(Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, (h * 0.90).toNumber(), Graphics.FONT_XTINY, WatchUi.loadResource(Rez.Strings.VolumeBackHint) as String, Graphics.TEXT_JUSTIFY_CENTER);
    }

    private function drawButton(dc as Dc, rect as Array<Number>, label as String) as Void {
        var x1 = rect[0];
        var y1 = rect[1];
        var bw = rect[2] - x1;
        var bh = rect[3] - y1;
        dc.setColor(Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.fillRoundedRectangle(x1, y1, bw, bh, 14);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(x1 + bw / 2, y1 + bh / 2, Graphics.FONT_LARGE, label, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    function hitTest(x as Number, y as Number) as Symbol? {
        if (inRect(x, y, minusRect)) {
            return :minus;
        }
        if (inRect(x, y, plusRect)) {
            return :plus;
        }
        return null;
    }

    private function inRect(x as Number, y as Number, rect as Array<Number>) as Boolean {
        return x >= rect[0] && x <= rect[2] && y >= rect[1] && y <= rect[3];
    }

    function adjust(delta as Number) as Void {
        getApp().setVolume(getApp().getVolume() + delta);
        WatchUi.requestUpdate();
    }
}
