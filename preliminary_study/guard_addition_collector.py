#!/usr/bin/env python
# -*- coding: utf-8 -*-
import os
import re
import datetime
import subprocess
import multiprocessing



def init(l):
    global lock
    lock = l


def check_guard_change(repo_dir, commit_id):
    """
    :param repo_dir:
    :param commit_id:
    :return:
    """

    found_change_regex = r'\{\+.*\+\}'
    found_guard_addition_regex = r'\{\+.*(isDebugEnabled|isTraceEnabled).*\+\}'
    found_guard_addition_regex_sepcial = r'\{\+.*(isTrac).*\+\}'

    added_line = 0
    deleted_line = 0
    added_guard = 0

    os.chdir(repo_dir)
    git_show_cmd = ["git", "show", commit_id, "--word-diff-regex=[^[:space:]]", "--", "*.java", ":!*src/test/java*"]
    git_show_stat_cmd = ["git", "show", commit_id, "--oneline", "--shortstat", "--", "*.java", ":!*src/test/java*"]
    try:
        show_result = subprocess.check_output(git_show_stat_cmd)
        diff = show_result.decode(encoding="utf-8", errors="ignore").splitlines()
        if len(diff) == 2:
            stat_line = diff[1]
            insertions = re.findall(r'\d+ insertions', stat_line)
            if len(insertions) != 0:
                added_line = int(re.findall(r'\d+ ', insertions[0])[0])
            deletions = re.findall(r'\d+ deletions', stat_line)
            if len(deletions) != 0:
                deleted_line = int(re.findall(r'\d+ ', deletions[0])[0])
    except subprocess.CalledProcessError as call_err:
        print("git show {commit_id} --shortstat fail because '{msg}'".format(
            commit_id=commit_id, msg=call_err.output.decode(encoding="utf-8", errors="ignore")))

    try:
        show_result = subprocess.check_output(git_show_cmd)
        diff = show_result.decode(encoding="utf-8", errors="ignore")
        lines = diff.splitlines()
        for i, line in enumerate(lines):
            if i == len(lines)-1:
                break
            found_guard_change = re.findall(found_guard_addition_regex, line)
            found_guard_change_special = re.findall(found_guard_addition_regex_sepcial, line)
            if len(found_guard_change) != 0 or len(found_guard_change_special) != 0:
                next_line = lines[i+1]
                next_line_change = re.findall(found_change_regex, next_line)
                for change in next_line_change:
                    next_line = next_line.replace(change, "")
                next_line = next_line.strip()
                # found guard addition, and next line is not entire line addition
                if next_line != "":
                    added_guard += 1
    except subprocess.CalledProcessError as call_err:
        print("git show {commit_id} fail because '{msg}'".format(
            commit_id=commit_id, msg=call_err.output.decode(encoding="utf-8", errors="ignore")))
    except Exception as e:
        print("git show {commit_id} fail".format(commit_id=commit_id))
        print(e)

    return "{commit_id},{added_line},{deleted_line},{added_guard}".format(
                commit_id=commit_id,
                added_line=added_line,
                deleted_line=deleted_line,
                added_guard=added_guard
            )


def collect(repo_name, repo_dir, log_path):
    if not os.path.exists(repo_dir):
        print("Repository does not exist!")
        return
    os.chdir(repo_dir)
    git_log_cmd = ['git', 'log', '--no-merges', '--pretty=format:"%H"']
    try:
        commit_id_result = subprocess.check_output(git_log_cmd)
        commit_id_result = commit_id_result.decode("UTF-8", errors="ignore")
        commit_ids = [commit_id.replace("\"", "") for commit_id in commit_id_result.split("\n")]
        print("{num} commits for {repo}".format(num=len(commit_ids), repo=repo_name))
        l = multiprocessing.Lock()
        pool = multiprocessing.Pool(processes=4, initializer=init, initargs=(l,))
        results = []
        for commit_id in commit_ids:
            results.append(pool.apply_async(check_guard_change, (repo_dir, commit_id)))
        pool.close()
        pool.join()
        with open(os.path.join(log_path, repo_name + "_guard_addition.log"), "w") as output:
            output.write("commit_id,added_line,deleted_line,guard_addition\n")
            for result in results:
                output.write(result.get() + "\n")
    except subprocess.CalledProcessError as call_err:
        print("{repo} git log fail because '{msg}'".format(
            repo=repo_name, msg=call_err.output.decode(encoding="utf-8", errors="ignore")))


if __name__ == "__main__":
    collect("zookeeper", "E:\OSS\zookeeper", "E:\OSS\zookeeper")
