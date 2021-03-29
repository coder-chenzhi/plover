import os

if __name__ == '__main__':
    data_dir = "E:/IDEAWorkspace/autologguards/logs/zookeeper_profile_logs0/logs"
    output_file = "E:/IDEAWorkspace/autologguards/logs/zookeeper_profile_logs0/summary.log"

    output = open(output_file, "w")
    for f_name in os.listdir(data_dir):
        print(f_name)
        with open(os.path.join(data_dir, f_name), "r", encoding="utf-8", errors="ignore") as log_file:
            for line in log_file.readlines():
                if "[LoggingOverhead]" in line:
                    time = line[:line.find("[")-1]
                    logging_id = line[line.find("LoggingID:"): line.find(", Time consumption")].replace("LoggingID:", "")
                    time_cost = line[line.find("Time consumption"):line.find(", Space consumption: ")]\
                        .replace("Time consumption: ", "").replace(" ns", "")
                    space_cost = line[line.find("Space"):].replace("Space consumption: ", "").replace(" bytes", "")
                    output.write("\t".join([f_name, time, logging_id, time_cost, space_cost]))

    output.close()

