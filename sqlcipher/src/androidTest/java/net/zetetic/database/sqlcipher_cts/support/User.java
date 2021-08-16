package net.zetetic.database.sqlcipher_cts.support;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class User {
  @PrimaryKey
  public int uid;
  @ColumnInfo(name = "first_name")
  public String firstName;
  @ColumnInfo(name = "last_name")
  public String lastName;

  public User(String firstName, String lastName) {
    this.firstName = firstName;
    this.lastName = lastName;
  }
}
