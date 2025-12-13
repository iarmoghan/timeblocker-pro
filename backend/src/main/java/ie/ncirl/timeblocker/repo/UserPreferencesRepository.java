package ie.ncirl.timeblocker.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import ie.ncirl.timeblocker.domain.UserPreferences;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> { }
