package jadx.tests.internal.conditions;

import jadx.api.InternalJadxTest;
import jadx.core.dex.nodes.ClassNode;

import org.junit.Test;

import static jadx.tests.utils.JadxMatchers.containsOne;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

public class TestConditions9 extends InternalJadxTest {

	public static class TestCls {
		public void test(boolean a, int b) throws Exception {
			if (!a || (b >= 0 && b <= 11)) {
				System.out.println('1');
			} else {
				System.out.println('2');
			}
		}
	}

	@Test
	public void test() {
		ClassNode cls = getClassNode(TestCls.class);
		String code = cls.getCode().toString();
		System.out.println(code);

		assertThat(code, containsOne("if (!a || (b >= 0 && b <= 11)) {"));
		assertThat(code, containsOne("System.out.println('1');"));
		assertThat(code, containsOne("} else {"));
		assertThat(code, containsOne("System.out.println('2');"));
		assertThat(code, not(containsString("return;")));
	}
}