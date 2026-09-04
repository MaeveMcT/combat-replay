package com.combatreplay;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JSlider;

final class EventTimeline extends JSlider
{
	private CombatRecording recording;

	EventTimeline()
	{
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				if (recording != null && recording.ticks.size() > 1)
				{
					int width = Math.max(1, getWidth() - 16);
					setValue(Math.max(0, Math.min(getMaximum(),
						(int) Math.round((event.getX() - 8) * getMaximum() / (double) width))));
				}
			}
		});
	}

	void setRecording(CombatRecording recording)
	{
		this.recording = recording;
		setMinimum(0);
		setMaximum(recording == null ? 0 : Math.max(0, recording.ticks.size() - 1));
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		super.paintComponent(graphics);
		if (recording == null || recording.ticks.size() < 2)
		{
			return;
		}
		int width = Math.max(1, getWidth() - 16);
		for (int tick = 0; tick < recording.ticks.size(); tick++)
		{
			Color category = markerColor(recording.ticks.get(tick));
			if (category != null)
			{
				int x = 8 + (int) Math.round(tick * width / (double) (recording.ticks.size() - 1));
				graphics.setColor(category);
				graphics.fillRect(x - 1, 2, 3, 8);
			}
		}
	}

	private static Color markerColor(RecordedTick tick)
	{
		boolean inventory = tick.containerChanges != null && !tick.containerChanges.isEmpty();
		for (RecordedEvent event : tick.events)
		{
			switch (event.type)
			{
				case "DEATH": return new Color(245, 245, 245);
				case "MECHANIC": return new Color(190, 90, 255);
				case "HITSPLAT": return new Color(235, 75, 75);
				case "ITEM_ACTION": return new Color(255, 180, 55);
				case "PRAYER_CHANGE": return new Color(80, 180, 255);
				default: break;
			}
		}
		return inventory ? new Color(255, 215, 80) : null;
	}
}
