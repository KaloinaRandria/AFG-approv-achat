package afg.achat.afgApprovAchat.upload;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

@Service
public class FileSystemStorageService implements StorageService {

	private final Path rootLocation;

	@Autowired
	public FileSystemStorageService(StorageProperties properties) {
		this.rootLocation = Paths.get(properties.getLocation());
	}

	@Override
	@PostConstruct
	public void init() {
		try {
			Files.createDirectories(rootLocation);
		} catch (IOException e) {
			throw new StorageException("Could not initialize storage location", e);
		}
	}

	private static final List<String> ALLOWED_TYPES = List.of(
			"image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf"
	);

	private static final List<String> ALLOWED_EXTENSIONS = List.of(
			".jpg", ".jpeg", ".png", ".gif", ".webp", ".pdf"
	);

	@Override
	public String store(MultipartFile file, String ref) {
		String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
		String filename = StringUtils.cleanPath(ref + "_" + originalFilename);

		try {
			if (file.isEmpty()) {
				throw new StorageException("Failed to store empty file " + filename);
			}
			if (filename.contains("..")) {
				throw new StorageException(
						"Cannot store file with relative path outside current directory " + filename);
			}

			// ✅ Validation de l'extension
			String ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
			if (!ALLOWED_EXTENSIONS.contains(ext)) {
				throw new StorageException("Format de fichier non autorisé : " + ext +
						". Seuls les images et PDF sont acceptés.");
			}

			// ✅ Validation du Content-Type
			String contentType = file.getContentType();
			if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
				throw new StorageException("Type de fichier non autorisé : " + contentType +
						". Seuls les images et PDF sont acceptés.");
			}

			try (InputStream inputStream = file.getInputStream()) {
				Files.copy(inputStream, this.rootLocation.resolve(filename),
						StandardCopyOption.REPLACE_EXISTING);
			}

		} catch (IOException e) {
			throw new StorageException("Failed to store file " + filename, e);
		}

		return filename;
	}

	@Override
	public Stream<Path> loadAll() {
		try {
			return Files.walk(this.rootLocation, 1).filter(path -> !path.equals(this.rootLocation))
					.map(this.rootLocation::relativize);
		} catch (IOException e) {
			throw new StorageException("Failed to read stored files", e);
		}

	}

	@Override
	public Path load(String filename) {
		return rootLocation.resolve(filename);
	}

	@Override
	public Resource loadAsResource(String filename) {
		try {
			Path file = load(filename);
			Resource resource = new UrlResource(file.toUri());
			if (resource.exists() || resource.isReadable()) {
				return resource;
			} else {
				throw new FileNotFoundException("Could not read file: " + filename);
			}
		} catch (MalformedURLException e) {
			throw new FileNotFoundException("Could not read file: " + filename, e);
		}
	}

	@Override
	public void deleteAll() {
		FileSystemUtils.deleteRecursively(rootLocation.toFile());
	}
}