package software.wings.beans;

import software.wings.service.intfc.FileService.FileBucket;

// TODO: Auto-generated Javadoc

/**
 * Created by anubhaw on 5/25/16.
 */
public abstract class CopyCommandUnit extends CommandUnit {
  private String fileId;
  private FileBucket fileBucket;
  private String destinationFilePath;

  /**
   * Instantiates a new copy command unit.
   *
   * @param commandUnitType the command unit type
   */
  public CopyCommandUnit(CommandUnitType commandUnitType) {
    super(commandUnitType);
  }

  /**
   * Gets file id.
   *
   * @return the file id
   */
  public String getFileId() {
    return fileId;
  }

  /**
   * Sets file id.
   *
   * @param fileId the file id
   */
  public void setFileId(String fileId) {
    this.fileId = fileId;
  }

  /**
   * Gets file bucket.
   *
   * @return the file bucket
   */
  public FileBucket getFileBucket() {
    return fileBucket;
  }

  /**
   * Sets file bucket.
   *
   * @param fileBucket the file bucket
   */
  public void setFileBucket(FileBucket fileBucket) {
    this.fileBucket = fileBucket;
  }

  /**
   * Gets destination file path.
   *
   * @return the destination file path
   */
  public String getDestinationFilePath() {
    return destinationFilePath;
  }

  /**
   * Sets destination file path.
   *
   * @param destinationFilePath the destination file path
   */
  public void setDestinationFilePath(String destinationFilePath) {
    this.destinationFilePath = destinationFilePath;
  }
}
