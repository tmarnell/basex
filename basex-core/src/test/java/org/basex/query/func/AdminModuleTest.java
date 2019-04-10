package org.basex.query.func;

import static org.basex.query.QueryError.*;
import static org.basex.query.func.Function.*;

import org.basex.*;
import org.basex.core.cmd.*;
import org.junit.*;
import org.junit.Test;

/**
 * This class tests the functions of the Admin Module.
 *
 * @author BaseX Team 2005-19, BSD License
 * @author Christian Gruen
 */
public final class AdminModuleTest extends SandboxTest {
  /** Initialize tests. */
  @BeforeClass public static void init() {
    execute(new CreateDB(NAME));
  }

  /** Finalize tests. */
  @AfterClass public static void finish() {
    execute(new DropDB(NAME));
  }

  /** Test method. */
  @Test public void deleteLogs() {
    final Function func = _ADMIN_DELETE_LOGS;
    // no logging data exists in the sandbox
    error(func.args("2001-01-01"), WHICHRES_X);
  }

  /** Test method. */
  @Test public void logs() {
    final Function func = _ADMIN_LOGS;
    // no logging data exists in the sandbox
    query(func.args(), "");
    error(func.args("2001-01-01"), WHICHRES_X);
  }

  /** Test method. */
  @Test public void sessions() {
    final Function func = _ADMIN_SESSIONS;
    // can only be tested in client/server mode
    query(func.args(), "");
  }

  /** Test method. */
  @Test public void writeLog() {
    final Function func = _ADMIN_WRITE_LOG;
    // no logging data exists in the sandbox
    error(func.args("", "X "), ADMIN_TYPE_X);
    error(func.args("", "X\r"), ADMIN_TYPE_X);
  }
}
