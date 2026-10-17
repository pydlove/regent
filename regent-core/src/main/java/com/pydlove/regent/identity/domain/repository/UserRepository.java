package com.pydlove.regent.identity.domain.repository;

import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.model.Username;

import java.util.Optional;

public interface UserRepository {

    boolean existsByUsername(Username username);

    boolean existsByEmail(Email email);

    Optional<User> findByUsername(Username username);

    Optional<User> findByEmail(Email email);

    Optional<User> findById(UserId id);

    void save(User user);

    void update(User user);
}
