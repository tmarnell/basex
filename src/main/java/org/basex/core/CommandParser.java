package org.basex.core;

import static org.basex.core.Text.*;
import static org.basex.util.Token.*;
import org.basex.core.Commands.Cmd;
import org.basex.core.Commands.CmdCreate;
import org.basex.core.Commands.CmdDrop;
import org.basex.core.Commands.CmdImport;
import org.basex.core.Commands.CmdIndex;
import org.basex.core.Commands.CmdIndexInfo;
import org.basex.core.Commands.CmdInfo;
import org.basex.core.Commands.CmdPerm;
import org.basex.core.Commands.CmdShow;
import org.basex.core.proc.Add;
import org.basex.core.proc.AlterUser;
import org.basex.core.proc.Check;
import org.basex.core.proc.CreateColl;
import org.basex.core.proc.CreateUser;
import org.basex.core.proc.Cs;
import org.basex.core.proc.Close;
import org.basex.core.proc.CreateDB;
import org.basex.core.proc.CreateFS;
import org.basex.core.proc.CreateIndex;
import org.basex.core.proc.CreateMAB;
import org.basex.core.proc.Delete;
import org.basex.core.proc.DropDB;
import org.basex.core.proc.DropIndex;
import org.basex.core.proc.DropUser;
import org.basex.core.proc.Exit;
import org.basex.core.proc.Export;
import org.basex.core.proc.Find;
import org.basex.core.proc.Get;
import org.basex.core.proc.Grant;
import org.basex.core.proc.Help;
import org.basex.core.proc.ImportColl;
import org.basex.core.proc.ImportDB;
import org.basex.core.proc.Info;
import org.basex.core.proc.InfoDB;
import org.basex.core.proc.InfoIndex;
import org.basex.core.proc.InfoTable;
import org.basex.core.proc.Kill;
import org.basex.core.proc.List;
import org.basex.core.proc.Open;
import org.basex.core.proc.Optimize;
import org.basex.core.proc.Password;
import org.basex.core.proc.ShowSessions;
import org.basex.core.proc.ShowUsers;
import org.basex.core.proc.Run;
import org.basex.core.proc.Set;
import org.basex.core.proc.ShowDatabases;
import org.basex.core.proc.XQuery;
import org.basex.query.QueryContext;
import org.basex.query.QueryException;
import org.basex.query.QueryParser;
import org.basex.util.Array;
import org.basex.util.InputParser;
import org.basex.util.Levenshtein;
import org.basex.util.StringList;
import org.basex.util.TokenBuilder;

/**
 * This is a parser for command strings, creating {@link Proc} instances.
 * Several commands can be formulated in one string and separated by semicolons.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-10, ISC License
 * @author Christian Gruen
 */
public final class CommandParser extends InputParser {
  /** Context. */
  private final Context ctx;
  /** Flag for including internal commands. */
  private final boolean internal;

  /**
   * Constructor, parsing the input queries.
   * @param in query input
   * @param c context
   */
  public CommandParser(final String in, final Context c) {
    this(in, c, false);
  }

  /**
   * Constructor, parsing internal commands.
   * @param in query input
   * @param c context
   * @param i internal flag
   */
  public CommandParser(final String in, final Context c, final boolean i) {
    ctx = c;
    internal = i;
    init(in);
  }

  /**
   * Parses the input and returns a command list.
   * @return commands
   * @throws QueryException query exception
   */
  public Proc[] parse() throws QueryException {
    Proc[] list = new Proc[0];
    if(!more()) return list;

    while(true) {
      final Cmd cmd = consume(Cmd.class, null);
      list = Array.add(list, parse(cmd));
      consumeWS();
      if(!more()) return list;
      if(!consume(';')) help(null, cmd);
    }
  }

  /**
   * Parses a single command.
   * @param cmd command
   * @return process
   * @throws QueryException query exception
   */
  private Proc parse(final Cmd cmd) throws QueryException {
    switch(cmd) {
      case CREATE: case C:
        switch(consume(CmdCreate.class, cmd)) {
          case DATABASE: case DB:
            return new CreateDB(string(cmd), name(null));
          case COLLECTION: case COLL:
            return new CreateColl(name(cmd));
          case INDEX:
            return new CreateIndex(consume(CmdIndex.class, cmd));
          case FS:
            return new CreateFS(string(cmd), name(cmd));
          case MAB:
            return new CreateMAB(string(cmd), name(null));
          case USER:
            return new CreateUser(name(cmd), string(null));
        }
        break;
      case ALTER:
        key(USER, cmd);
        return new AlterUser(name(cmd), string(null));
      case OPEN: case O:
        return new Open(name(cmd));
      case CHECK:
        return new Check(string(cmd));
      case ADD:
        return new Add(string(cmd));
      case DELETE:
        return new Delete(string(cmd));
      case INFO: case I:
        switch(consume(CmdInfo.class, cmd)) {
          case NULL:
            return new Info();
          case DATABASE: case DB:
            return new InfoDB();
          case INDEX:
            return new InfoIndex(consume(CmdIndexInfo.class, null));
          case TABLE:
            String arg1 = number(null);
            final String arg2 = arg1 != null ? number(null) : null;
            if(arg1 == null) arg1 = xquery(null);
            return new InfoTable(arg1, arg2);
        }
        break;
      case CLOSE:
        return new Close();
      case LIST:
        return new List();
      case DROP:
        switch(consume(CmdDrop.class, cmd)) {
          case DATABASE: case DB:
            return new DropDB(name(cmd));
          case INDEX:
            return new DropIndex(consume(CmdIndex.class, cmd));
          case USER:
            final String name = name(cmd);
            final String db = key(ON, null) ? name(cmd) : null;
            return new DropUser(name, db);
        }
        break;
      case OPTIMIZE:
        return new Optimize();
      case EXPORT:
        return new Export(string(cmd), string(null));
      case IMPORT:
        switch(consume(CmdImport.class, cmd)) {
          case DATABASE: case DB:
            return new ImportDB(name(cmd), leftover(null));
          case COLLECTION: case COLL:
            return new ImportColl(name(cmd), leftover(null));
        }
        break;        
      case XQUERY: case X:
        return new XQuery(xquery(cmd));
      case RUN:
        return new Run(string(cmd));
      case FIND:
        return new Find(string(cmd));
      case CS:
        return new Cs(xquery(cmd));
      case GET:
        return new Get(name(cmd));
      case SET:
        return new Set(name(cmd), string(null));
      case PASSWORD:
        return new Password(string(null));
      case HELP:
        String hc = name(null);
        if(hc != null) {
          qp = qm;
          hc = consume(Cmd.class, cmd).toString();
        }
        return new Help(hc);
      case EXIT: case QUIT: case Q:
        return new Exit();
      case KILL:
        return new Kill(name(cmd));
      case SHOW:
        switch(consume(CmdShow.class, cmd)) {
          case DATABASES:
            return new ShowDatabases();
          case SESSIONS:
            return new ShowSessions();
          case USERS:
            final String db = key(ON, null) ? name(cmd) : null;
            return new ShowUsers(db);
          default:
        }
        break;
      case GRANT:
        final CmdPerm perm = consume(CmdPerm.class, cmd);
        if(perm == null) help(null, cmd);
        final String db = key(ON, null) ? name(cmd) : null;
        key(TO, cmd);
        return db == null ? new Grant(perm, name(cmd)) :
          new Grant(perm, name(cmd), db);
      default:
    }
    return null;
  }

  /**
   * Parses and returns a string. Quotes can be used to include spaces.
   * @param cmd referring command; if specified, the result must not be empty
   * @return path
   * @throws QueryException query exception
   */
  private String string(final Cmd cmd) throws QueryException {
    final TokenBuilder tb = new TokenBuilder();
    consumeWS();
    boolean q = false;
    while(more()) {
      final char c = curr();
      if(!q && (c <= ' ' || c == ';')) break;
      if(c == '"') q ^= true;
      else tb.add(c);
      consume();
    }
    return finish(cmd, tb);
  }
  
  /**
   * Parses and returns the whole leftover of the string.
   * @param cmd referring command; if specified, the result must not be empty
   * @return xml string
   * @throws QueryException query exception
   */
  private String leftover(final Cmd cmd) throws QueryException {
    consumeWS();
    final TokenBuilder tb = new TokenBuilder();
    while(more()) {
      final char c = curr();
      tb.add(c);
      consume();
    }
    return finish(cmd, tb);
  }

  /**
   * Parses and returns an xquery expression.
   * @param cmd referring command; if specified, the result must not be empty
   * @return path
   * @throws QueryException query exception
   */
  private String xquery(final Cmd cmd) throws QueryException {
    consumeWS();
    final TokenBuilder tb = new TokenBuilder();
    if(more() && !curr(';')) {
      final QueryParser p = new QueryParser(new QueryContext(ctx));
      p.init(qu);
      p.qp = qp;
      p.parse(null, false);
      tb.add(qu.substring(qp, p.qp));
      qp = p.qp;
    }
    return finish(cmd, tb);
  }

  /**
   * Parses and returns a name. A name can include letters, digits, dashes
   * and periods.
   * @param cmd referring command; if specified, the result must not be empty
   * @return name
   * @throws QueryException query exception
   */
  private String name(final Cmd cmd) throws QueryException {
    consumeWS();
    final TokenBuilder tb = new TokenBuilder();
    while(letterOrDigit(curr()) || curr('.') || curr('-')) tb.add(consume());
    return finish(cmd, tb);
  }

  /**
   * Parses and returns the specified keyword.
   * @param key token to be parsed
   * @param cmd referring command; if specified, the result must not be empty
   * @return position
   * @throws QueryException query exception
   */
  private boolean key(final String key, final Cmd cmd) throws QueryException {
    consumeWS();
    final boolean ok = consume(key) || consume(key.toLowerCase());
    if(!ok && cmd != null) help(null, cmd);
    consumeWS();
    return ok;
  }

  /**
   * Parses and returns a name.
   * @param cmd referring command; if specified, the result must not be empty
   * @param s input string
   * @return name
   * @throws QueryException query exception
   */
  private String finish(final Cmd cmd, final TokenBuilder s)
      throws QueryException {
    if(s.size() != 0) return s.toString();
    if(cmd != null) help(null, cmd);
    return null;
  }

  /**
   * Parses and returns a number.
   * @param cmd referring command; if specified, the result must not be empty
   * @return name
   * @throws QueryException query exception
   */
  private String number(final Cmd cmd) throws QueryException {
    consumeWS();
    final TokenBuilder tb = new TokenBuilder();
    if(curr() == '-') tb.add(consume());
    while(digit(curr())) tb.add(consume());
    return finish(cmd, tb);
  }

  /**
   * Returns the index of the found string or throws an error.
   * @param cmp possible completions
   * @param par parent command
   * @param <E> token type
   * @return index
   * @throws QueryException query exception
   */
  private <E extends Enum<E>> E consume(final Class<E> cmp, final Cmd par)
      throws QueryException {

    final String token = name(null);
    try {
      // return command reference; allow empty strings as input ("NULL")
      final String t = token == null ? "NULL" : token.toUpperCase();
      final E cmd = Enum.valueOf(cmp, t);
      if(!Cmd.class.isInstance(cmd)) return cmd;
      final Cmd c = Cmd.class.cast(cmd);
      if(!c.help() && (internal || !c.internal())) return cmd;
    } catch(final IllegalArgumentException ex) { }

    final Enum<?>[] alt = list(cmp, token);
    if(token == null) {
      // no command found
      if(par == null) error(list(alt), CMDNO);
      // show available command extensions
      help(list(alt), par);
    }

    // find similar commands
    final byte[] name = lc(token(token));
    final Levenshtein ls = new Levenshtein();
    for(final Enum<?> s : list(cmp, null)) {
      final byte[] sm = lc(token(s.name().toLowerCase()));
      if(ls.similar(name, sm, 0) && Cmd.class.isInstance(s))
        error(list(alt), CMDSIMILAR, name, sm);
    }

    // unknown command
    if(par == null) error(list(alt), CMDWHICH, token);
    // show available command extensions
    help(list(alt), par);
    return null;
  }

  /**
   * Prints some command info.
   * @param alt input alternatives
   * @param cmd input completions
   * @throws QueryException query exception
   */
  private void help(final StringList alt, final Cmd cmd) throws QueryException {
    error(alt, PROCSYNTAX, cmd.help(true));
  }

  /**
   * Returns the command list.
   * @param <T> token type
   * @param en enumeration
   * @param i user input
   * @return completions
   */
  private <T extends Enum<T>> Enum<?>[] list(final Class<T> en,
      final String i) {

    Enum<?>[] list = new Enum<?>[0];
    final String t = i == null ? "" : i.toUpperCase();
    for(final Enum<?> e : en.getEnumConstants()) {
      if(Cmd.class.isInstance(e)) {
        final Cmd c = Cmd.class.cast(e);
        if(c.help() || c.hidden() || c.internal()) continue;
      }
      if(e.name().startsWith(t)) {
        final int s = list.length;
        final Enum<?>[] tmp = new Enum<?>[s + 1];
        System.arraycopy(list, 0, tmp, 0, s);
        tmp[s] = e;
        list = tmp;
      }
    }
    return list;
  }

  /**
   * Throws an error.
   * @param comp input completions
   * @param m message
   * @param e extension
   * @throws QueryException query exception
   */
  private void error(final StringList comp, final String m, final Object... e)
      throws QueryException {
    final QueryException qe = new QueryException(m, e);
    qe.complete(this, comp);
    throw qe;
  }

  /**
   * Converts the specified commands into a string list.
   * @param comp input completions
   * @return string list
   */
  private StringList list(final Enum<?>[] comp) {
    final StringList list = new StringList();
    for(final Enum<?> c : comp) list.add(c.name().toLowerCase());
    return list;
  }
}
