package io.unityfoundation.dds.permissions.manager;

import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.model.user.UserRepository;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

@Singleton
public class Bootstrap {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;

    public Bootstrap(UserRepository userRepository, GroupRepository groupRepository) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
    }

    @EventListener
    public void devData(ServerStartupEvent event) {
        User justin = userRepository.save(new User("Justin", "Wilson", "jwilson@test.test"));
        User kevin = userRepository.save(new User("Kevin", "Stanley", "kstanley@test.test"));
        User max = userRepository.save(new User("Max", "Montes", "montesm@test.test", true));
        userRepository.save(new User("Jeff", "Brown", "jeff@test.test"));
        userRepository.save(new User("Julian", "Gracia", "jgracia@test.test"));
        userRepository.save(new User("Daniel", "Bellone", "dbellonen@test.test"));

        Group alphaGroup = groupRepository.save(new Group("Alpha"));
        alphaGroup.setUsers(Set.of(kevin, max));
        alphaGroup.setAdmins(Set.of(justin));
        groupRepository.update(alphaGroup);

        groupRepository.save(new Group("Beta"));
        groupRepository.save(new Group("Gamma"));
        groupRepository.save(new Group("Delta"));
    }
}
