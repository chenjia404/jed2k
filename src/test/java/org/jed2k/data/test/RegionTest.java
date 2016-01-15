package org.jed2k.data.test;

import static junit.framework.Assert.assertEquals;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;
import org.jed2k.data.Range;
import org.jed2k.data.Region;

public class RegionTest {

	@Test
	public void testIntersectSubsSubs() {
		Region rg = new Region(Range.make(100L, 200L));
		assertEquals(rg, new Region(Range.make(100L, 200L)));
		rg.sub(new Range(150L, 180L));
		assertEquals(rg, new Region(new Range[] {new Range(100L, 150L), new Range(180L, 200L)}));		
		rg.sub(Range.make(120L, 130L)).sub(Range.make(180L, 190L));
		assertThat(rg, is(new Region(new Range[] {Range.make(100L, 120L), Range.make(130L, 150L), Range.make(190L, 200L)})));		
	}
	
	@Test
	public void testNonIntersectSub() {
		Region rg = new Region(Range.make(100L, 200L));
		rg.sub(Range.make(0L, 100L));
		rg.sub(Range.make(201L, 220L));
		assertEquals(rg, new Region(Range.make(100L, 200L)));
		assertThat(rg, is(not(new Region(Range.make(1L, 2L)))));
	}
	
	@Test
	public void testPartialIntersectSub() {
		Region rg = new Region(Range.make(100L, 200L));
		rg.sub(Range.make(0L, 110L)).sub(Range.make(190L, 220L));
		assertThat(rg, is(new Region(Range.make(110L, 190L))));
	}
}
