package com.rr.bosses.yama;

import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.*;

import javax.inject.Inject;
import java.util.Objects;

/**
 * Most of this code was copied from the Charge Calculator plugin.
 */
public class DuoNameAutoFillWidget
{

    @Inject
    private YamaUtilitiesPlugin plugin;

    private Client client;
    private Widget duoDisplayNameWidget;

    public DuoNameAutoFillWidget(Widget parent, Client client)
    {
        this.client = client;
        
        if (parent == null)
        {
            return;
        }

        this.duoDisplayNameWidget = parent.createChild(WidgetType.TEXT);

        if (Objects.requireNonNull(parent.getChildren()).length == 2 && !this.duoDisplayNameWidget.isHidden())
        {
            prep(this.duoDisplayNameWidget, parent.getWidth()/4*(-1));
            prep(Objects.requireNonNull(parent.getChild(0)), parent.getWidth()/4);
        }
        else
        {
            prep(this.duoDisplayNameWidget, 0);
        }

    }

    private void prep(Widget widget, int x)
    {
        widget.setTextColor(0xFF);
        widget.setFontId(FontID.PLAIN_12);

        widget.setOriginalX(x);
        widget.setOriginalY(0);
        widget.setOriginalHeight(36);
        widget.setOriginalWidth(252);

        widget.setWidthMode(WidgetSizeMode.MINUS);
        widget.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
        widget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
        widget.setXTextAlignment(WidgetTextAlignment.CENTER);
        widget.setYTextAlignment(WidgetTextAlignment.CENTER);

        widget.setHasListener(true);
        widget.setOnMouseRepeatListener((JavaScriptCallback) ev -> widget.setTextColor(0xFFFFFF));
        widget.setOnMouseLeaveListener((JavaScriptCallback) ev -> widget.setTextColor(0x800000));

        widget.revalidate();
    }

    public void showWidget(String displayName)
    {
        duoDisplayNameWidget.setText("<col=000000>Party Plugin:</col> " + displayName);
        duoDisplayNameWidget.setAction(0, "Join");
        duoDisplayNameWidget.setOnOpListener((JavaScriptCallback) ev -> {
            Objects.requireNonNull(client.getWidget(InterfaceID.Chatbox.MES_TEXT2)).setText(displayName + "*");
            client.setVarcStrValue(VarClientID.MESLAYERINPUT, displayName);
        });
    }
}