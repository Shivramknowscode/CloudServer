<?php

class Application extends \Symfony\Component\Console\Application
{
    public function __construct()
    {
        parent::__construct("");
        $this->add(new RunCommand());
        $this->setDefaultCommand("run");
    }

    public static function go(): void
    {
        (new self())->run();
    }

}