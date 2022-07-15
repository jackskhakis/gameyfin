package de.grimsi.gameyfin.service;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.entities.BlacklistEntry;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.igdb.IgdbWrapper;
import de.grimsi.gameyfin.mapper.GameMapper;
import de.grimsi.gameyfin.repositories.BlacklistRepository;
import de.grimsi.gameyfin.repositories.DetectedGameRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
@Service
public class FilesystemService {
    @Value("${gameyfin.root}")
    private String rootFolderPath;

    @Value("${gameyfin.file-extensions}")
    private List<String> possibleGameFileExtensions;

    @Autowired
    private IgdbWrapper igdbWrapper;

    @Autowired
    private DetectedGameRepository detectedGameRepository;

    @Autowired
    private BlacklistRepository blacklistRepository;

    public List<Path> getGameFiles() {

        Path rootFolder = Path.of(rootFolderPath);

        try (Stream<Path> stream = Files.list(rootFolder)) {
            // return all sub-folders (non-recursive) and files that have an extension that indicates that they are a downloadable file
            return stream
                    .filter(p -> Files.isDirectory(p) || possibleGameFileExtensions.contains(FilenameUtils.getExtension(p.getFileName().toString())))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Error while opening root folder", e);
        }
    }

    public List<String> getGameFileNames() {
        return this.getGameFiles().stream().map(this::getFilename).toList();
    }

    public void scanGameLibrary() {
        log.info("Starting scan...");

        AtomicInteger newBlacklistCounter = new AtomicInteger();

        List<Path> gameFiles = getGameFiles();

        // Filter out the games we already know and the ones we already tried to map to a game without success
        gameFiles = gameFiles.stream()
                .filter(g -> !detectedGameRepository.existsByPath(g.toString()))
                .filter(g -> !blacklistRepository.existsByPath(g.toString()))
                .peek(p -> log.info("Found new potential game: {}", p))
                .toList();

        // For each new game, load the info from IGDB
        // If a game is not found on IGDB, blacklist the path so we won't query the API later on for the same path
        List<DetectedGame> newDetectedGames = gameFiles.parallelStream()
                .map(p -> {
                    Optional<Igdb.Game> optionalGame = igdbWrapper.searchForGameByTitle(getFilename(p));
                    return optionalGame.map(game -> Map.entry(p, game)).or(() -> {
                        blacklistRepository.save(new BlacklistEntry(p.toString()));
                        newBlacklistCounter.getAndIncrement();
                        log.info("Added path '{}' to blacklist", p);
                        return Optional.empty();
                    });
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(e -> GameMapper.toDetectedGame(e.getValue(), e.getKey()))
                .toList();

        newDetectedGames = detectedGameRepository.saveAll(newDetectedGames);

        log.info("Scan finished: Found {} new games, deleted {} games, backlisted {} files/folders, {} games total.", newDetectedGames.size(), "NOT_IMPLEMENTED_YET", newBlacklistCounter.get(), detectedGameRepository.count());
    }

    private String getFilename(Path p) {
        return FilenameUtils.getBaseName(p.toString());
    }
}