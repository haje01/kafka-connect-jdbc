package io.confluent.connect.jdbc.sink.dialect;

import org.apache.kafka.connect.data.Schema;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.confluent.connect.jdbc.sink.common.StringBuilderUtil;

import static io.confluent.connect.jdbc.sink.common.StringBuilderUtil.joinToBuilder;
import static io.confluent.connect.jdbc.sink.common.StringBuilderUtil.stringIdentityTransform;
import static io.confluent.connect.jdbc.sink.common.StringBuilderUtil.stringSurroundTransform;

public class PostgreSQLDialect extends DbDialect {

  // The user is responsible for escaping the columns otherwise create table A and create table "A" is not the same

  public PostgreSQLDialect() {
    super(getSqlTypeMap(), "\"", "\"");
  }

  private static Map<Schema.Type, String> getSqlTypeMap() {
    Map<Schema.Type, String> map = new HashMap<>();
    map.put(Schema.Type.INT8, "SMALLINT");
    map.put(Schema.Type.INT16, "SMALLINT");
    map.put(Schema.Type.INT32, "INT");
    map.put(Schema.Type.INT64, "BIGINT");
    map.put(Schema.Type.FLOAT32, "FLOAT");
    map.put(Schema.Type.FLOAT64, "DOUBLE PRECISION");
    map.put(Schema.Type.BOOLEAN, "BOOLEAN");
    map.put(Schema.Type.STRING, "TEXT");
    map.put(Schema.Type.BYTES, "BYTEA");
    return map;
  }

  @Override
  public String getUpsertQuery(final String table, final List<String> cols, final List<String> keyCols) {
    if (table == null || table.trim().length() == 0) {
      throw new IllegalArgumentException("<table=> is not valid. A non null non empty string expected");
    }

    if (keyCols == null || keyCols.size() == 0) {
      throw new IllegalArgumentException(
          String.format("Your SQL table %s does not have any primary key/s. You can only UPSERT when your SQL table has primary key/s defined",
                        table)
      );
    }

    final StringBuilder builder = new StringBuilder();
    builder.append("INSERT INTO ");
    builder.append(handleTableName(table));
    builder.append(" (");
    joinToBuilder(builder, ",", cols, keyCols, stringSurroundTransform(escapeColumnNamesStart, escapeColumnNamesEnd));
    builder.append(") VALUES (");
    joinToBuilder(builder, ",", Collections.nCopies(cols.size() + keyCols.size(), "?"), stringIdentityTransform());
    builder.append(") ON CONFLICT (");
    joinToBuilder(builder, ",", keyCols, stringSurroundTransform(escapeColumnNamesStart, escapeColumnNamesEnd));
    builder.append(") DO UPDATE SET ");
    joinToBuilder(
        builder,
        ",",
        cols,
        new StringBuilderUtil.Transform<String>() {
          @Override
          public void apply(StringBuilder builder, String col) {
            builder.append(escapeColumnNamesStart);
            builder.append(col);
            builder.append(escapeColumnNamesEnd);
            builder.append("=EXCLUDED.");
            builder.append(escapeColumnNamesStart);
            builder.append(col);
            builder.append(escapeColumnNamesEnd);
          }
        }
    );
    return builder.toString();
  }
}
