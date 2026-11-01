package com.brewpos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;

/** Needs a reachable grp_brewposdb: runs when DB_PASSWORD is set in the environment or .env. */
@SpringBootTest
@EnabledIf("com.brewpos.DbTestCondition#isDbConfigured")
class BrewPosApplicationTests {

	@Test
	void contextLoads() {
	}

}
