package com.pydlove.regent.identity.infrastructure.persistence;

import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.model.Username;
import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisUserRepository implements UserRepository {

    private final UserMapper mapper;

    public MyBatisUserRepository(UserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean existsByUsername(Username username) {
        return mapper.countByUsername(username.value()) > 0;
    }

    @Override
    public boolean existsByEmail(Email email) {
        return mapper.countByEmail(email.value()) > 0;
    }

    @Override
    public Optional<User> findByUsername(Username username) {
        return Optional.ofNullable(mapper.findByUsername(username.value()))
                .map(UserDataMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return Optional.ofNullable(mapper.findByEmail(email.value()))
                .map(UserDataMapper::toDomain);
    }

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.ofNullable(mapper.findById(id.value()))
                .map(UserDataMapper::toDomain);
    }

    @Override
    public void save(User user) {
        UserData data = UserDataMapper.toData(user);
        try {
            mapper.insert(data);
        } catch (DuplicateKeyException exception) {
            throw new DomainException(
                    user.getEmail() == null ? "用户名已存在" : "该邮箱已注册，请直接登录",
                    exception);
        }
        user.assignIdentity(UserId.of(data.getId()));
    }

    @Override
    public void update(User user) {
        if (user.getId() == null) {
            throw new DomainException("未持久化的用户不能更新");
        }
        if (mapper.update(UserDataMapper.toData(user)) != 1) {
            throw new DomainException("用户不存在或已被删除：" + user.getId().value());
        }
    }
}
